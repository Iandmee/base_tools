/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "dex_bytecode.h"
#include "common.h"

#include <assert.h>
#include <array>

namespace dex {

Opcode OpcodeFromBytecode(u2 bytecode) {
  Opcode opcode = Opcode(bytecode & 0xff);
  assert(opcode != OP_UNUSED_FF);
  return opcode;
}

// Table that maps each opcode to the index type implied by that opcode
static constexpr std::array<InstructionIndexType, kNumPackedOpcodes>
    gInstructionIndexTypeTable = {
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexStringRef,
        kIndexStringRef,    kIndexTypeRef,      kIndexNone,
        kIndexNone,         kIndexTypeRef,      kIndexTypeRef,
        kIndexNone,         kIndexTypeRef,      kIndexTypeRef,
        kIndexTypeRef,      kIndexTypeRef,      kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexUnknown,
        kIndexUnknown,      kIndexUnknown,      kIndexUnknown,
        kIndexUnknown,      kIndexUnknown,      kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexFieldRef,     kIndexFieldRef,
        kIndexFieldRef,     kIndexFieldRef,     kIndexFieldRef,
        kIndexFieldRef,     kIndexFieldRef,     kIndexFieldRef,
        kIndexFieldRef,     kIndexFieldRef,     kIndexFieldRef,
        kIndexFieldRef,     kIndexFieldRef,     kIndexFieldRef,
        kIndexFieldRef,     kIndexFieldRef,     kIndexFieldRef,
        kIndexFieldRef,     kIndexFieldRef,     kIndexFieldRef,
        kIndexFieldRef,     kIndexFieldRef,     kIndexFieldRef,
        kIndexFieldRef,     kIndexFieldRef,     kIndexFieldRef,
        kIndexFieldRef,     kIndexFieldRef,     kIndexMethodRef,
        kIndexMethodRef,    kIndexMethodRef,    kIndexMethodRef,
        kIndexMethodRef,    kIndexUnknown,      kIndexMethodRef,
        kIndexMethodRef,    kIndexMethodRef,    kIndexMethodRef,
        kIndexMethodRef,    kIndexUnknown,      kIndexUnknown,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexNone,
        kIndexNone,         kIndexNone,         kIndexFieldRef,
        kIndexFieldRef,     kIndexFieldRef,     kIndexFieldRef,
        kIndexFieldRef,     kIndexFieldRef,     kIndexFieldRef,
        kIndexFieldRef,     kIndexFieldRef,     kIndexUnknown,
        kIndexVaries,       kIndexInlineMethod, kIndexInlineMethod,
        kIndexMethodRef,    kIndexNone,         kIndexFieldOffset,
        kIndexFieldOffset,  kIndexFieldOffset,  kIndexFieldOffset,
        kIndexFieldOffset,  kIndexFieldOffset,  kIndexVtableOffset,
        kIndexVtableOffset, kIndexVtableOffset, kIndexVtableOffset,
        kIndexFieldRef,     kIndexFieldRef,     kIndexFieldRef,
        kIndexUnknown,
};

InstructionIndexType GetIndexTypeFromOpcode(Opcode opcode) {
  return gInstructionIndexTypeTable[opcode];
}

// Table that maps each opcode to the full width of instructions that
// use that opcode, in (16-bit) code units. Unimplemented opcodes as
// well as the "breakpoint" opcode have a width of zero.
static constexpr std::array<u1, kNumPackedOpcodes> gInstructionWidthTable = {
    1, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 3, 2, 2, 3,
    5, 2, 2, 3, 2, 1, 1, 2, 2, 1, 2, 2, 3, 3, 3, 1, 1, 2, 3, 3, 3, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 0, 3, 3, 3, 3,
    3, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 0, 2, 3, 3,
    3, 1, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 2, 2, 2, 0,
};

size_t GetWidthFromOpcode(Opcode opcode) {
  return gInstructionWidthTable[opcode];
}

size_t GetWidthFromBytecode(const u2* bytecode) {
  size_t width = 0;
  if (*bytecode == kPackedSwitchSignature) {
    width = 4 + bytecode[1] * 2;
  } else if (*bytecode == kSparseSwitchSignature) {
    width = 2 + bytecode[1] * 4;
  } else if (*bytecode == kArrayDataSignature) {
    u2 elemWidth = bytecode[1];
    u4 len = bytecode[2] | (((u4)bytecode[3]) << 16);
    // The plus 1 is to round up for odd size and width.
    width = 4 + (elemWidth * len + 1) / 2;
  } else {
    width = GetWidthFromOpcode(OpcodeFromBytecode(bytecode[0]));
  }
  return width;
}

// Table that maps each opcode to the instruction format
static constexpr std::array<InstructionFormat, kNumPackedOpcodes>
    gInstructionFormatTable = {
        kFmt10x,  kFmt12x,  kFmt22x,  kFmt32x,  kFmt12x,  kFmt22x,  kFmt32x,
        kFmt12x,  kFmt22x,  kFmt32x,  kFmt11x,  kFmt11x,  kFmt11x,  kFmt11x,
        kFmt10x,  kFmt11x,  kFmt11x,  kFmt11x,  kFmt11n,  kFmt21s,  kFmt31i,
        kFmt21h,  kFmt21s,  kFmt31i,  kFmt51l,  kFmt21h,  kFmt21c,  kFmt31c,
        kFmt21c,  kFmt11x,  kFmt11x,  kFmt21c,  kFmt22c,  kFmt12x,  kFmt21c,
        kFmt22c,  kFmt35c,  kFmt3rc,  kFmt31t,  kFmt11x,  kFmt10t,  kFmt20t,
        kFmt30t,  kFmt31t,  kFmt31t,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,
        kFmt23x,  kFmt22t,  kFmt22t,  kFmt22t,  kFmt22t,  kFmt22t,  kFmt22t,
        kFmt21t,  kFmt21t,  kFmt21t,  kFmt21t,  kFmt21t,  kFmt21t,  kFmt00x,
        kFmt00x,  kFmt00x,  kFmt00x,  kFmt00x,  kFmt00x,  kFmt23x,  kFmt23x,
        kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,
        kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt22c,  kFmt22c,
        kFmt22c,  kFmt22c,  kFmt22c,  kFmt22c,  kFmt22c,  kFmt22c,  kFmt22c,
        kFmt22c,  kFmt22c,  kFmt22c,  kFmt22c,  kFmt22c,  kFmt21c,  kFmt21c,
        kFmt21c,  kFmt21c,  kFmt21c,  kFmt21c,  kFmt21c,  kFmt21c,  kFmt21c,
        kFmt21c,  kFmt21c,  kFmt21c,  kFmt21c,  kFmt21c,  kFmt35c,  kFmt35c,
        kFmt35c,  kFmt35c,  kFmt35c,  kFmt00x,  kFmt3rc,  kFmt3rc,  kFmt3rc,
        kFmt3rc,  kFmt3rc,  kFmt00x,  kFmt00x,  kFmt12x,  kFmt12x,  kFmt12x,
        kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,
        kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,
        kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt23x,  kFmt23x,  kFmt23x,
        kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,
        kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,
        kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,
        kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,  kFmt23x,
        kFmt23x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,
        kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,
        kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,
        kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,
        kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt12x,  kFmt22s,  kFmt22s,
        kFmt22s,  kFmt22s,  kFmt22s,  kFmt22s,  kFmt22s,  kFmt22s,  kFmt22b,
        kFmt22b,  kFmt22b,  kFmt22b,  kFmt22b,  kFmt22b,  kFmt22b,  kFmt22b,
        kFmt22b,  kFmt22b,  kFmt22b,  kFmt22c,  kFmt22c,  kFmt21c,  kFmt21c,
        kFmt22c,  kFmt22c,  kFmt22c,  kFmt21c,  kFmt21c,  kFmt00x,  kFmt20bc,
        kFmt35mi, kFmt3rmi, kFmt35c,  kFmt10x,  kFmt22cs, kFmt22cs, kFmt22cs,
        kFmt22cs, kFmt22cs, kFmt22cs, kFmt35ms, kFmt3rms, kFmt35ms, kFmt3rms,
        kFmt22c,  kFmt21c,  kFmt21c,  kFmt00x,
};

InstructionFormat GetFormatFromOpcode(Opcode opcode) {
  return gInstructionFormatTable[opcode];
}

// Dalvik opcode names.
static constexpr std::array<const char*, kNumPackedOpcodes> gOpcodeNames = {
    "nop",
    "move",
    "move/from16",
    "move/16",
    "move-wide",
    "move-wide/from16",
    "move-wide/16",
    "move-object",
    "move-object/from16",
    "move-object/16",
    "move-result",
    "move-result-wide",
    "move-result-object",
    "move-exception",
    "return-void",
    "return",
    "return-wide",
    "return-object",
    "const/4",
    "const/16",
    "const",
    "const/high16",
    "const-wide/16",
    "const-wide/32",
    "const-wide",
    "const-wide/high16",
    "const-string",
    "const-string/jumbo",
    "const-class",
    "monitor-enter",
    "monitor-exit",
    "check-cast",
    "instance-of",
    "array-length",
    "new-instance",
    "new-array",
    "filled-new-array",
    "filled-new-array/range",
    "fill-array-data",
    "throw",
    "goto",
    "goto/16",
    "goto/32",
    "packed-switch",
    "sparse-switch",
    "cmpl-float",
    "cmpg-float",
    "cmpl-double",
    "cmpg-double",
    "cmp-long",
    "if-eq",
    "if-ne",
    "if-lt",
    "if-ge",
    "if-gt",
    "if-le",
    "if-eqz",
    "if-nez",
    "if-ltz",
    "if-gez",
    "if-gtz",
    "if-lez",
    "unused-3e",
    "unused-3f",
    "unused-40",
    "unused-41",
    "unused-42",
    "unused-43",
    "aget",
    "aget-wide",
    "aget-object",
    "aget-boolean",
    "aget-byte",
    "aget-char",
    "aget-short",
    "aput",
    "aput-wide",
    "aput-object",
    "aput-boolean",
    "aput-byte",
    "aput-char",
    "aput-short",
    "iget",
    "iget-wide",
    "iget-object",
    "iget-boolean",
    "iget-byte",
    "iget-char",
    "iget-short",
    "iput",
    "iput-wide",
    "iput-object",
    "iput-boolean",
    "iput-byte",
    "iput-char",
    "iput-short",
    "sget",
    "sget-wide",
    "sget-object",
    "sget-boolean",
    "sget-byte",
    "sget-char",
    "sget-short",
    "sput",
    "sput-wide",
    "sput-object",
    "sput-boolean",
    "sput-byte",
    "sput-char",
    "sput-short",
    "invoke-virtual",
    "invoke-super",
    "invoke-direct",
    "invoke-static",
    "invoke-interface",
    "unused-73",
    "invoke-virtual/range",
    "invoke-super/range",
    "invoke-direct/range",
    "invoke-static/range",
    "invoke-interface/range",
    "unused-79",
    "unused-7a",
    "neg-int",
    "not-int",
    "neg-long",
    "not-long",
    "neg-float",
    "neg-double",
    "int-to-long",
    "int-to-float",
    "int-to-double",
    "long-to-int",
    "long-to-float",
    "long-to-double",
    "float-to-int",
    "float-to-long",
    "float-to-double",
    "double-to-int",
    "double-to-long",
    "double-to-float",
    "int-to-byte",
    "int-to-char",
    "int-to-short",
    "add-int",
    "sub-int",
    "mul-int",
    "div-int",
    "rem-int",
    "and-int",
    "or-int",
    "xor-int",
    "shl-int",
    "shr-int",
    "ushr-int",
    "add-long",
    "sub-long",
    "mul-long",
    "div-long",
    "rem-long",
    "and-long",
    "or-long",
    "xor-long",
    "shl-long",
    "shr-long",
    "ushr-long",
    "add-float",
    "sub-float",
    "mul-float",
    "div-float",
    "rem-float",
    "add-double",
    "sub-double",
    "mul-double",
    "div-double",
    "rem-double",
    "add-int/2addr",
    "sub-int/2addr",
    "mul-int/2addr",
    "div-int/2addr",
    "rem-int/2addr",
    "and-int/2addr",
    "or-int/2addr",
    "xor-int/2addr",
    "shl-int/2addr",
    "shr-int/2addr",
    "ushr-int/2addr",
    "add-long/2addr",
    "sub-long/2addr",
    "mul-long/2addr",
    "div-long/2addr",
    "rem-long/2addr",
    "and-long/2addr",
    "or-long/2addr",
    "xor-long/2addr",
    "shl-long/2addr",
    "shr-long/2addr",
    "ushr-long/2addr",
    "add-float/2addr",
    "sub-float/2addr",
    "mul-float/2addr",
    "div-float/2addr",
    "rem-float/2addr",
    "add-double/2addr",
    "sub-double/2addr",
    "mul-double/2addr",
    "div-double/2addr",
    "rem-double/2addr",
    "add-int/lit16",
    "rsub-int",
    "mul-int/lit16",
    "div-int/lit16",
    "rem-int/lit16",
    "and-int/lit16",
    "or-int/lit16",
    "xor-int/lit16",
    "add-int/lit8",
    "rsub-int/lit8",
    "mul-int/lit8",
    "div-int/lit8",
    "rem-int/lit8",
    "and-int/lit8",
    "or-int/lit8",
    "xor-int/lit8",
    "shl-int/lit8",
    "shr-int/lit8",
    "ushr-int/lit8",
    "+iget-volatile",
    "+iput-volatile",
    "+sget-volatile",
    "+sput-volatile",
    "+iget-object-volatile",
    "+iget-wide-volatile",
    "+iput-wide-volatile",
    "+sget-wide-volatile",
    "+sput-wide-volatile",
    "^breakpoint",
    "^throw-verification-error",
    "+execute-inline",
    "+execute-inline/range",
    "+invoke-object-init/range",
    "+return-void-barrier",
    "+iget-quick",
    "+iget-wide-quick",
    "+iget-object-quick",
    "+iput-quick",
    "+iput-wide-quick",
    "+iput-object-quick",
    "+invoke-virtual-quick",
    "+invoke-virtual-quick/range",
    "+invoke-super-quick",
    "+invoke-super-quick/range",
    "+iput-object-volatile",
    "+sget-object-volatile",
    "+sput-object-volatile",
    "unused-ff",
};

const char* GetOpcodeName(Opcode opcode) { return gOpcodeNames[opcode]; }

// Helpers for DecodeInstruction()
static u4 InstA(u2 inst) { return (inst >> 8) & 0x0f; }
static u4 InstB(u2 inst) { return inst >> 12; }
static u4 InstAA(u2 inst) { return inst >> 8; }

// Helper for DecodeInstruction()
static u4 FetchU4(const u2* ptr) {
  return ptr[0] | (u4(ptr[1]) << 16);
}

// Helper for DecodeInstruction()
static u8 FetchU8(const u2* ptr) {
  return FetchU4(ptr) | (u8(FetchU4(ptr + 2)) << 32);
}

// Decode a Dalvik bytecode and extract the individual fields
Instruction DecodeInstruction(const u2* bytecode) {
  Instruction dec = {};

  u2 inst = bytecode[0];
  Opcode opcode = OpcodeFromBytecode(inst);
  InstructionFormat format = GetFormatFromOpcode(opcode);

  dec.opcode = opcode;

  switch (format) {
    case kFmt10x:  // op
      break;
    case kFmt12x:  // op vA, vB
      dec.vA = InstA(inst);
      dec.vB = InstB(inst);
      break;
    case kFmt11n:  // op vA, #+B
      dec.vA = InstA(inst);
      dec.vB = s4(InstB(inst) << 28) >> 28;  // sign extend 4-bit value
      break;
    case kFmt11x:  // op vAA
      dec.vA = InstAA(inst);
      break;
    case kFmt10t:                 // op +AA
      dec.vA = s1(InstAA(inst));  // sign-extend 8-bit value
      break;
    case kFmt20t:                // op +AAAA
      dec.vA = s2(bytecode[1]);  // sign-extend 16-bit value
      break;
    case kFmt20bc:  // [opt] op AA, thing@BBBB
    case kFmt21c:   // op vAA, thing@BBBB
    case kFmt22x:   // op vAA, vBBBB
      dec.vA = InstAA(inst);
      dec.vB = bytecode[1];
      break;
    case kFmt21s:  // op vAA, #+BBBB
    case kFmt21t:  // op vAA, +BBBB
      dec.vA = InstAA(inst);
      dec.vB = s2(bytecode[1]);  // sign-extend 16-bit value
      break;
    case kFmt21h:  // op vAA, #+BBBB0000[00000000]
      dec.vA = InstAA(inst);
      // The value should be treated as right-zero-extended, but we don't
      // actually do that here. Among other things, we don't know if it's
      // the top bits of a 32- or 64-bit value.
      dec.vB = bytecode[1];
      break;
    case kFmt23x:  // op vAA, vBB, vCC
      dec.vA = InstAA(inst);
      dec.vB = bytecode[1] & 0xff;
      dec.vC = bytecode[1] >> 8;
      break;
    case kFmt22b:  // op vAA, vBB, #+CC
      dec.vA = InstAA(inst);
      dec.vB = bytecode[1] & 0xff;
      dec.vC = s1(bytecode[1] >> 8);  // sign-extend 8-bit value
      break;
    case kFmt22s:  // op vA, vB, #+CCCC
    case kFmt22t:  // op vA, vB, +CCCC
      dec.vA = InstA(inst);
      dec.vB = InstB(inst);
      dec.vC = s2(bytecode[1]);  // sign-extend 16-bit value
      break;
    case kFmt22c:   // op vA, vB, thing@CCCC
    case kFmt22cs:  // [opt] op vA, vB, field offset CCCC
      dec.vA = InstA(inst);
      dec.vB = InstB(inst);
      dec.vC = bytecode[1];
      break;
    case kFmt30t:  // op +AAAAAAAA
      dec.vA = FetchU4(bytecode + 1);
      break;
    case kFmt31t:  // op vAA, +BBBBBBBB
    case kFmt31c:  // op vAA, string@BBBBBBBB
      dec.vA = InstAA(inst);
      dec.vB = FetchU4(bytecode + 1);
      break;
    case kFmt32x:  // op vAAAA, vBBBB
      dec.vA = bytecode[1];
      dec.vB = bytecode[2];
      break;
    case kFmt31i:  // op vAA, #+BBBBBBBB
      dec.vA = InstAA(inst);
      dec.vB = FetchU4(bytecode + 1);
      break;
    case kFmt35c:   // op {vC, vD, vE, vF, vG}, thing@BBBB
    case kFmt35ms:  // [opt] invoke-virtual+super
    case kFmt35mi:  // [opt] inline invoke
    {
      dec.vA = InstB(inst);  // This is labeled A in the spec.
      dec.vB = bytecode[1];

      u2 regList = bytecode[2];

      // Copy the argument registers into the arg[] array, and
      // also copy the first argument (if any) into vC. (The
      // Instruction structure doesn't have separate
      // fields for {vD, vE, vF, vG}, so there's no need to make
      // copies of those.) Note that cases 5..2 fall through.
      switch (dec.vA) {
        case 5:
          // A fifth arg is verboten for inline invokes
          CHECK(format != kFmt35mi);

          // Per note at the top of this format decoder, the
          // fifth argument comes from the A field in the
          // instruction, but it's labeled G in the spec.
          dec.arg[4] = InstA(inst);
        // fallthrough
        case 4:
          dec.arg[3] = (regList >> 12) & 0x0f;
        // fallthrough
        case 3:
          dec.arg[2] = (regList >> 8) & 0x0f;
        // fallthrough
        case 2:
          dec.arg[1] = (regList >> 4) & 0x0f;
        // fallthrough
        case 1:
          dec.vC = dec.arg[0] = regList & 0x0f;
        // fallthrough
        case 0:
          // Valid, but no need to do anything
          break;
        default:
          CHECK(!"Invalid arg count in 35c/35ms/35mi");
          break;
      }
    } break;
    case kFmt3rc:   // op {vCCCC .. v(CCCC+AA-1)}, meth@BBBB
    case kFmt3rms:  // [opt] invoke-virtual+super/range
    case kFmt3rmi:  // [opt] execute-inline/range
      dec.vA = InstAA(inst);
      dec.vB = bytecode[1];
      dec.vC = bytecode[2];
      break;
    case kFmt51l:  // op vAA, #+BBBBBBBBBBBBBBBB
      dec.vA = InstAA(inst);
      dec.vB_wide = FetchU8(bytecode + 1);
      break;
    default:
      CHECK(!"Can't decode unexpected format");
      break;
  }

  return dec;
}

}  // namespace dex
