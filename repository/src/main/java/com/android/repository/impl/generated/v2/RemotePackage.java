
package com.android.repository.impl.generated.v2;

import com.android.repository.impl.meta.RemotePackageImpl;
import com.android.repository.impl.meta.TrimStringAdapter;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * DO NOT EDIT This file was generated by xjc from repo-common-02.xsd. Any changes will be lost upon
 * recompilation of the schema. See the schema file for instructions on running xjc.
 *
 * <p>A remote package, available for download.
 *
 * <p>Java class for remotePackage complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="remotePackage"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;group ref="{http://schemas.android.com/repository/android/common/02}packageFields"/&gt;
 *         &lt;element name="channelRef" type="{http://schemas.android.com/repository/android/common/02}channelRefType" minOccurs="0"/&gt;
 *         &lt;element name="archives" type="{http://schemas.android.com/repository/android/common/02}archivesType"/&gt;
 *       &lt;/sequence&gt;
 *       &lt;attGroup ref="{http://schemas.android.com/repository/android/common/02}packageAttributes"/&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
        name = "remotePackage",
        propOrder = {
            "typeDetails",
            "revision",
            "displayName",
            "usesLicense",
            "dependencies",
            "channelRef",
            "archives"
        })
@SuppressWarnings({"override", "unchecked"})
public class RemotePackage extends RemotePackageImpl {

    @XmlElement(name = "type-details", required = true)
    protected com.android.repository.impl.generated.v2.TypeDetails typeDetails;
    @XmlElement(required = true)
    protected com.android.repository.impl.generated.v2.RevisionType revision;
    @XmlElement(name = "display-name", required = true)
    @XmlJavaTypeAdapter(TrimStringAdapter.class)
    protected String displayName;
    @XmlElement(name = "uses-license")
    protected LicenseRefType usesLicense;
    protected DependenciesType dependencies;
    protected ChannelRefType channelRef;
    @XmlElement(required = true)
    protected ArchivesType archives;
    @XmlAttribute(name = "path", required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    protected String path;
    @XmlAttribute(name = "obsolete")
    protected Boolean obsolete;

    /**
     * Gets the value of the typeDetails property.
     *
     * @return possible object is {@link com.android.repository.impl.generated.v2.TypeDetails }
     */
    public com.android.repository.impl.generated.v2.TypeDetails getTypeDetails() {
        return typeDetails;
    }

    /**
     * Sets the value of the typeDetails property.
     *
     * @param value allowed object is {@link com.android.repository.impl.generated.v2.TypeDetails }
     */
    public void setTypeDetailsInternal(com.android.repository.impl.generated.v2.TypeDetails value) {
        this.typeDetails = value;
    }

    /**
     * Gets the value of the revision property.
     *
     * @return possible object is {@link com.android.repository.impl.generated.v2.RevisionType }
     */
    public com.android.repository.impl.generated.v2.RevisionType getRevision() {
        return revision;
    }

    /**
     * Sets the value of the revision property.
     *
     * @param value allowed object is {@link com.android.repository.impl.generated.v2.RevisionType }
     */
    public void setRevisionInternal(com.android.repository.impl.generated.v2.RevisionType value) {
        this.revision = value;
    }

    /**
     * Gets the value of the displayName property.
     *
     * @return possible object is {@link String }
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the value of the displayName property.
     *
     * @param value allowed object is {@link String }
     */
    public void setDisplayName(String value) {
        this.displayName = value;
    }

    /**
     * Gets the value of the usesLicense property.
     *
     * @return possible object is {@link LicenseRefType }
     */
    public LicenseRefType getUsesLicense() {
        return usesLicense;
    }

    /**
     * Sets the value of the usesLicense property.
     *
     * @param value allowed object is {@link LicenseRefType }
     */
    public void setUsesLicenseInternal(LicenseRefType value) {
        this.usesLicense = value;
    }

    /**
     * Gets the value of the dependencies property.
     *
     * @return possible object is {@link DependenciesType }
     */
    public DependenciesType getDependencies() {
        return dependencies;
    }

    /**
     * Sets the value of the dependencies property.
     *
     * @param value allowed object is {@link DependenciesType }
     */
    public void setDependenciesInternal(DependenciesType value) {
        this.dependencies = value;
    }

    /**
     * Gets the value of the channelRef property.
     *
     * @return possible object is {@link ChannelRefType }
     */
    public ChannelRefType getChannelRef() {
        return channelRef;
    }

    /**
     * Sets the value of the channelRef property.
     *
     * @param value allowed object is {@link ChannelRefType }
     */
    public void setChannelRefInternal(ChannelRefType value) {
        this.channelRef = value;
    }

    /**
     * Gets the value of the archives property.
     *
     * @return possible object is {@link ArchivesType }
     */
    public ArchivesType getArchives() {
        return archives;
    }

    /**
     * Sets the value of the archives property.
     *
     * @param value allowed object is {@link ArchivesType }
     */
    public void setArchivesInternal(ArchivesType value) {
        this.archives = value;
    }

    /**
     * Gets the value of the path property.
     *
     * @return possible object is {@link String }
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets the value of the path property.
     *
     * @param value allowed object is {@link String }
     */
    public void setPath(String value) {
        this.path = value;
    }

    /**
     * Gets the value of the obsolete property.
     *
     * @return possible object is {@link Boolean }
     */
    public Boolean isObsolete() {
        return obsolete;
    }

    /**
     * Sets the value of the obsolete property.
     *
     * @param value allowed object is {@link Boolean }
     */
    public void setObsolete(Boolean value) {
        this.obsolete = value;
    }

    public void setTypeDetails(com.android.repository.impl.meta.TypeDetails value) {
        setTypeDetailsInternal(((com.android.repository.impl.generated.v2.TypeDetails) value));
    }

    public void setRevision(com.android.repository.impl.meta.RevisionType value) {
        setRevisionInternal(((com.android.repository.impl.generated.v2.RevisionType) value));
    }

    public void setUsesLicense(com.android.repository.impl.meta.RepoPackageImpl.UsesLicense value) {
        setUsesLicenseInternal(((LicenseRefType) value));
    }

    public void setDependencies(
            com.android.repository.impl.meta.RepoPackageImpl.Dependencies value) {
        setDependenciesInternal(((DependenciesType) value));
    }

    public void setChannelRef(RemotePackageImpl.ChannelRef value) {
        setChannelRefInternal(((ChannelRefType) value));
    }

    public void setArchives(com.android.repository.impl.meta.RepoPackageImpl.Archives value) {
        setArchivesInternal(((ArchivesType) value));
    }

    public ObjectFactory createFactory() {
        return new ObjectFactory();
    }

}
