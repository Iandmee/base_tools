/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.builder.internal.packaging.zip;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.packaging.zip.utils.CloseableByteSource;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.primitives.Ints;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A stored entry represents a file in the zip. The entry may or may not be written to the zip
 * file.
 * <p>
 * Stored entries provide the operations that are related to the files themselves, not to the zip.
 * It is through the {@code StoredEntry} class that entries can be deleted ({@link #delete()},
 * open ({@link #open()}) or realigned ({@link #realign()}).
 * <p>
 * Entries are not created directly. They are created using
 * {@link ZFile#add(String, InputStream, boolean)} and obtained from the zip file
 * using {@link ZFile#get(String)} or {@link ZFile#entries()}.
 * <p>
 * Most of the data in the an entry is in the Central Directory Header. This includes the name,
 * compression method, file compressed and uncompressed sizes, CRC32 checksum, etc. The CDH can
 * be obtained using the {@link #getCentralDirectoryHeader()} method.
 */
public class StoredEntry {
    /**
     * Signature of the data descriptor.
     */
    private static final int DATA_DESC_SIGNATURE = 0x08074b50;

    /**
     * Local header field: signature.
     */
    private static final ZipField.F4 F_LOCAL_SIGNATURE = new ZipField.F4(0, 0x04034b50,
            "Signature");

    /**
     * Local header field: version to extract, should match the CDH's.
     */
    private static final ZipField.F2 F_VERSION_EXTRACT = new ZipField.F2(
            F_LOCAL_SIGNATURE.endOffset(), "Version to extract",
            new ZipFieldInvariantNonNegative());

    /**
     * Local header field: GP bit flag, should match the CDH's.
     */
    private static final ZipField.F2 F_GP_BIT = new ZipField.F2(F_VERSION_EXTRACT.endOffset(),
            "GP bit flag");

    /**
     * Local header field: compression method, should match the CDH's.
     */
    private static final ZipField.F2 F_METHOD = new ZipField.F2(F_GP_BIT.endOffset(),
            "Compression method", new ZipFieldInvariantNonNegative());

    /**
     * Local header field: last modification time, should match the CDH's.
     */
    private static final ZipField.F2 F_LAST_MOD_TIME = new ZipField.F2(F_METHOD.endOffset(),
            "Last modification time");

    /**
     * Local header field: last modification time, should match the CDH's.
     */
    private static final ZipField.F2 F_LAST_MOD_DATE = new ZipField.F2(F_LAST_MOD_TIME.endOffset(),
            "Last modification date");

    /**
     * Local header field: CRC32 checksum, should match the CDH's. 0 if there is no data.
     */
    private static final ZipField.F4 F_CRC32 = new ZipField.F4(F_LAST_MOD_DATE.endOffset(),
            "CRC32");

    /**
     * Local header field: compressed size, size the data takes in the zip file.
     */
    private static final ZipField.F4 F_COMPRESSED_SIZE = new ZipField.F4(F_CRC32.endOffset(),
            "Compressed size", new ZipFieldInvariantNonNegative());

    /**
     * Local header field: uncompressed size, size the data takes after extraction.
     */
    private static final ZipField.F4 F_UNCOMPRESSED_SIZE = new ZipField.F4(
            F_COMPRESSED_SIZE.endOffset(), "Uncompressed size", new ZipFieldInvariantNonNegative());

    /**
     * Local header field: length of the file name.
     */
    private static final ZipField.F2 F_FILE_NAME_LENGTH = new ZipField.F2(
            F_UNCOMPRESSED_SIZE.endOffset(), "@File name length",
            new ZipFieldInvariantNonNegative());

    /**
     * Local header filed: length of the extra field.
     */
    private static final ZipField.F2 F_EXTRA_LENGTH = new ZipField.F2(
            F_FILE_NAME_LENGTH.endOffset(), "Extra length", new ZipFieldInvariantNonNegative());

    /**
     * Local header size (fixed part, not counting file name or extra field).
     */
    private static final int FIXED_LOCAL_FILE_HEADER_SIZE = F_EXTRA_LENGTH.endOffset();

    /**
     * Type of entry.
     */
    @NonNull
    private StoredEntryType mType;

    /**
     * The central directory header with information about the file.
     */
    @NonNull
    private CentralDirectoryHeader mCdh;

    /**
     * The file this entry is associated with
     */
    @NonNull
    private ZFile mFile;

    /**
     * Has this entry been deleted?
     */
    private boolean mDeleted;

    /**
     * Extra field specified in the local directory.
     */
    private byte[] mLocalExtra;

    /**
     * Type of data descriptor associated with the entry.
     */
    @NonNull
    private DataDescriptorType mDataDescriptorType;

    /**
     * Source for this entry's data. If this entry is a directory, this source has to have zero
     * size.
     */
    @NonNull
    private ProcessedAndRawByteSources mSource;

    /**
     * Creates a new stored entry.
     *
     * @param header the header with the entry information; if the header does not contain an
     * offset it means that this entry is not yet written in the zip file
     * @param file the zip file containing the entry
     * @param source the entry's data source; it can be {@code null} only if the source can be
     * read from the zip file, that is, if {@code header.getOffset()} is non-negative
     * @throws IOException failed to create the entry
     */
    StoredEntry(@NonNull CentralDirectoryHeader header, @NonNull ZFile file,
            @Nullable ProcessedAndRawByteSources source) throws IOException {
        mCdh = header;
        mFile = file;
        mDeleted = false;

        if (header.getOffset() >= 0) {
            readLocalHeader();

            Preconditions.checkArgument(source == null, "Source was defined but contents already "
                    + "exist on file.");

            /*
             * Since the file is already in the zip, dynamically create a source that will read
             * the file from the zip when needed. The assignment is not really needed, but we
             * would get a warning because of the @NotNull otherwise.
             */
            mSource = createSourceFromZip();
        } else {
            /*
             * There is no local extra data for new files.
             */
            mLocalExtra = new byte[0];

            Preconditions.checkNotNull(source, "Source was not defined, but contents are not "
                    + "on file.");
            mSource = source;
        }

        /*
         * It seems that zip utilities store directories as names ending with "/".
         * This seems to be respected by all zip utilities although I could not find there anywhere
         * in the specification.
         */
        if (mCdh.getName().endsWith(Character.toString(ZFile.SEPARATOR))) {
            mType = StoredEntryType.DIRECTORY;
            Verify.verify(mSource.getProcessedByteSource().isEmpty(),
                    "Directory source is not empty.");
            Verify.verify(mCdh.getCrc32() == 0, "Directory has CRC32 = %s.", mCdh.getCrc32());
            Verify.verify(mCdh.getUncompressedSize() == 0, "Directory has uncompressed size = %s.",
                    mCdh.getUncompressedSize());

            /*
             * Some clever (OMG!) tools, like jar will actually try to compress the directory
             * contents and generate a 2 byte compressed data. Of course, the uncompressed size is
             * zero and we're just wasting space.
             */
            long compressedSize = mCdh.getCompressionInfoWithWait().getCompressedSize();
            Verify.verify(compressedSize == 0 || compressedSize == 2,
                    "Directory has compressed size = %s.", compressedSize);
        } else {
            mType = StoredEntryType.FILE;
        }

        /*
         * By default we assume there is no data descriptor but we may override this later on if
         * the CRC is marked as deferred in the header's GP Bit.
         */
        mDataDescriptorType = DataDescriptorType.NO_DATA_DESCRIPTOR;
        if (header.getGpBit().isDeferredCrc()) {
            /*
             * If the deferred CRC bit exists, then we have an extra descriptor field. This extra
             * field may have a signature.
             */
            Verify.verify(header.getOffset() >= 0, "Files that are not on disk cannot have the "
                    + "deferred CRC bit set.");
            readDataDescriptorRecord();
        }
    }

    /**
     * Obtains the size of the local header of this entry.
     *
     * @return the local header size in bytes
     */
    int getLocalHeaderSize() {
        Preconditions.checkState(!mDeleted, "mDeleted");
        return FIXED_LOCAL_FILE_HEADER_SIZE + mCdh.getEncodedFileName().length + mLocalExtra.length;
    }

    /**
     * Obtains the size of the whole entry on disk, including local header and data descriptor.
     * This method will wait until compression information is complete, if needed.
     *
     * @return the number of bytes
     * @throws IOException failed to get compression information
     */
    long getInFileSize() throws IOException {
        Preconditions.checkState(!mDeleted, "mDeleted");
        return mCdh.getCompressionInfoWithWait().getCompressedSize() + getLocalHeaderSize()
                + mDataDescriptorType.size;
    }

    /**
     * Obtains a stream that allows reading from the entry.
     *
     * @return a stream that will return as many bytes as the uncompressed entry size
     * @throws IOException failed to open the stream
     */
    @NonNull
    public InputStream open() throws IOException {
        return mSource.getProcessedByteSource().openStream();
    }

    /**
     * Obtains the contents of the file.
     *
     * @return a byte array with the contents of the file (uncompressed if the file was compressed)
     * @throws IOException failed to read the file
     */
    @NonNull
    public byte[] read() throws IOException {
        InputStream is = open();
        boolean threw = true;
        try {
            byte[] r = ByteStreams.toByteArray(is);
            threw = false;
            return r;
        } finally {
            Closeables.close(is, threw);
        }
    }

    /**
     * Obtains the type of entry.
     *
     * @return the type of entry
     */
    @NonNull
    public StoredEntryType getType() {
        Preconditions.checkState(!mDeleted, "mDeleted");
        return mType;
    }

    /**
     * Deletes this entry from the zip file. Invoking this method doesn't update the zip itself.
     * To eventually write updates to disk, {@link ZFile#update()} must be called.
     *
     * @throws IOException failed to delete the entry
     */
    public void delete() throws IOException {
        delete(true);
    }

    /**
     * Deletes this entry from the zip file. Invoking this method doesn't update the zip itself.
     * To eventually write updates to disk, {@link ZFile#update()} must be called.
     *
     * @param notify should listeners be notified of the deletion? This will only be
     * {@code false} if the entry is being removed as part of a replacement
     * @throws IOException failed to delete the entry
     */
    void delete(boolean notify) throws IOException {
        Preconditions.checkState(!mDeleted, "mDeleted");
        mFile.delete(this, notify);
        mDeleted = true;
        mSource.close();
    }

    /**
     * Obtains the CDH associated with this entry.
     *
     * @return the CDH
     */
    @NonNull
    public CentralDirectoryHeader getCentralDirectoryHeader() {
        return mCdh;
    }

    /**
     * Reads the file's local header and verifies that it matches the Central Directory
     * Header provided in the constructor. This method should only be called if the entry already
     * exists on disk; new entries do not have local headers.
     * <p>
     * This method will define the {@link #mLocalExtra} field that is only defined in the
     * local descriptor.
     *
     * @throws IOException failed to read the local header
     */
    private void readLocalHeader() throws IOException {
        byte[] localHeader = new byte[FIXED_LOCAL_FILE_HEADER_SIZE];
        mFile.directFullyRead(mCdh.getOffset(), localHeader);

        CentralDirectoryHeaderCompressInfo compressInfo = mCdh.getCompressionInfoWithWait();

        ByteSource byteSource = ByteSource.wrap(localHeader);
        F_LOCAL_SIGNATURE.verify(byteSource);
        F_VERSION_EXTRACT.verify(byteSource, compressInfo.getVersionExtract());
        F_GP_BIT.verify(byteSource, mCdh.getGpBit().getValue());
        F_METHOD.verify(byteSource, compressInfo.getMethod().methodCode);

        if (!mFile.areTimestampsIgnored()) {
            F_LAST_MOD_TIME.verify(byteSource, mCdh.getLastModTime());
            F_LAST_MOD_DATE.verify(byteSource, mCdh.getLastModDate());
        }

        if (mCdh.getGpBit().isDeferredCrc()) {
            F_CRC32.verify(byteSource, 0);
            F_COMPRESSED_SIZE.verify(byteSource, 0);
            F_UNCOMPRESSED_SIZE.verify(byteSource, 0);
        } else {
            F_CRC32.verify(byteSource, mCdh.getCrc32());
            F_COMPRESSED_SIZE.verify(byteSource, compressInfo.getCompressedSize());
            F_UNCOMPRESSED_SIZE.verify(byteSource, mCdh.getUncompressedSize());
        }

        F_FILE_NAME_LENGTH.verify(byteSource, mCdh.getEncodedFileName().length);
        long extraLength = F_EXTRA_LENGTH.read(byteSource);
        long fileNameStart = mCdh.getOffset() + F_EXTRA_LENGTH.endOffset();
        byte[] fileNameData = new byte[mCdh.getEncodedFileName().length];
        mFile.directFullyRead(fileNameStart, fileNameData);

        String fileName = EncodeUtils.decode(fileNameData, mCdh.getGpBit());
        if (!fileName.equals(mCdh.getName())) {
            throw new IOException("Central directory reports file as being named '" + mCdh.getName()
                    + "' but local header reports file being named '" + fileName + "'.");
        }

        long localExtraStart = fileNameStart + mCdh.getEncodedFileName().length;
        mLocalExtra = new byte[Ints.checkedCast(extraLength)];
        mFile.directFullyRead(localExtraStart, mLocalExtra);
    }

    /**
     * Reads the data descriptor record. This method can only be invoked once it is established
     * that a data descriptor does exist. It will read the data descriptor and check that the data
     * described there matches the data provided in the Central Directory.
     * <p>
     * This method will set the {@link #mDataDescriptorType} field to the appropriate type of
     * data descriptor record.
     *
     * @throws IOException failed to read the data descriptor record
     */
    private void readDataDescriptorRecord() throws IOException {
        CentralDirectoryHeaderCompressInfo compressInfo = mCdh.getCompressionInfoWithWait();

        long ddStart = mCdh.getOffset() + FIXED_LOCAL_FILE_HEADER_SIZE
                + mCdh.getName().length() + mLocalExtra.length + compressInfo.getCompressedSize();
        byte ddData[] = new byte[DataDescriptorType.DATA_DESCRIPTOR_WITH_SIGNATURE.size];
        mFile.directFullyRead(ddStart, ddData);

        ByteSource ddSource = ByteSource.wrap(ddData);

        ZipField.F4 signatureField = new ZipField.F4(0, "Data descriptor signature");
        long sig = signatureField.read(ddSource);
        if (sig == DATA_DESC_SIGNATURE) {
            mDataDescriptorType = DataDescriptorType.DATA_DESCRIPTOR_WITH_SIGNATURE;
            ddSource = ddSource.slice(4, ddSource.size());
        } else {
            mDataDescriptorType = DataDescriptorType.DATA_DESCRIPTOR_WITHOUT_SIGNATURE;
        }

        ZipField.F4 crc32Field = new ZipField.F4(0, "CRC32");
        ZipField.F4 compressedField = new ZipField.F4(crc32Field.endOffset(), "Compressed size");
        ZipField.F4 uncompressedField = new ZipField.F4(compressedField.endOffset(),
                "Uncompressed size");

        crc32Field.verify(ddSource, mCdh.getCrc32());
        compressedField.verify(ddSource, compressInfo.getCompressedSize());
        uncompressedField.verify(ddSource, mCdh.getUncompressedSize());
    }

    /**
     * Creates a new source that reads data from the zip.
     * @throws IOException failed to close the old source
     * @return the created source
     */
    private ProcessedAndRawByteSources createSourceFromZip() throws IOException {
        Preconditions.checkState(mCdh.getOffset() >= 0, "No data in zip. Cannot create source.");

        final CentralDirectoryHeaderCompressInfo compressInfo;
        try {
            compressInfo = mCdh.getCompressionInfoWithWait();
        } catch (IOException e) {
            throw new RuntimeException("IOException should never occur here because compression "
                    + "information should be immediately avaialble if reading from zip.", e);
        }

        /*
         * Create a source that will return whatever is on the zip file.
         */
        CloseableByteSource rawContents = new CloseableByteSource() {
            @Override
            public long size() throws IOException {
                return compressInfo.getCompressedSize();
            }

            @NonNull
            @Override
            public InputStream openStream() throws IOException {
                Preconditions.checkState(!mDeleted, "mDeleted");

                long dataStart = mCdh.getOffset() + getLocalHeaderSize();
                long dataEnd = dataStart + compressInfo.getCompressedSize();

                mFile.openReadOnly();
                return mFile.directOpen(dataStart, dataEnd);
            }

            @Override
            protected void innerClose() throws IOException {
                /*
                 * Nothing to do here.
                 */
            }
        };

        CloseableByteSource contents;

        /*
         * If the contents are deflated, wrap that source in an inflater source so we get the
         * uncompressed data.
         */
        if (compressInfo.getMethod() == CompressionMethod.DEFLATE) {
            contents = new InflaterByteSource(rawContents);
        } else {
            contents = rawContents;
        }

        return new ProcessedAndRawByteSources(contents, rawContents);
    }

    /**
     * Replaces {@link #mSource} with one that reads file data from the zip file.
     * @throws IOException failed to replace the source
     */
    void replaceSourceFromZip() throws IOException {
        ProcessedAndRawByteSources oldSource = mSource;
        mSource = createSourceFromZip();
        oldSource.close();
    }

    /**
     * Obtains the source data for this entry. This method can only be called for files, it
     * cannot be called for directories.
     *
     * @return the entry source
     */
    @NonNull
    ProcessedAndRawByteSources getSource() {
        return mSource;
    }

    /**
     * Obtains the type of data descriptor used in the entry.
     *
     * @return the type of data descriptor
     */
    @NonNull
    public DataDescriptorType getDataDescriptorType() {
        return mDataDescriptorType;
    }

    /**
     * Obtains the local header data.
     *
     * @return the header data
     * @throws IOException failed to get header byte data
     */
    @NonNull
    byte[] toHeaderData() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        CentralDirectoryHeaderCompressInfo compressInfo = mCdh.getCompressionInfoWithWait();

        F_LOCAL_SIGNATURE.write(out);
        F_VERSION_EXTRACT.write(out, compressInfo.getVersionExtract());
        F_GP_BIT.write(out, mCdh.getGpBit().getValue());
        F_METHOD.write(out, compressInfo.getMethod().methodCode);

        if (mFile.areTimestampsIgnored()) {
            F_LAST_MOD_TIME.write(out, 0);
            F_LAST_MOD_DATE.write(out, 0);
        } else {
            F_LAST_MOD_TIME.write(out, mCdh.getLastModTime());
            F_LAST_MOD_DATE.write(out, mCdh.getLastModDate());
        }

        F_CRC32.write(out, mCdh.getCrc32());
        F_COMPRESSED_SIZE.write(out, compressInfo.getCompressedSize());
        F_UNCOMPRESSED_SIZE.write(out, mCdh.getUncompressedSize());
        F_FILE_NAME_LENGTH.write(out, mCdh.getEncodedFileName().length);
        F_EXTRA_LENGTH.write(out, mLocalExtra.length);

        out.write(mCdh.getEncodedFileName());
        out.write(mLocalExtra);

        return out.toByteArray();
    }

    /**
     * Requests that this entry be realigned. If this entry is already aligned according to the
     * rules in {@link ZFile} then this method does nothing. Otherwise it will move the file's data
     * into memory and place it in a different area of the zip.
     *
     * @return has this file been changed? Note that if the entry has not yet been written on the
     * file, realignment does not count as a change as nothing needs to be updated in the file;
     * also, if the entry has been changed, this object may have been marked as deleted and a new
     * stored entry may need to be fetched from the file
     * @throws IOException failed to realign the entry; the entry may no longer exist in the zip
     * file
     */
    public boolean realign() throws IOException {
        Preconditions.checkState(!mDeleted, "Entry has been deleted.");

        return mFile.realign(this);
    }
}
