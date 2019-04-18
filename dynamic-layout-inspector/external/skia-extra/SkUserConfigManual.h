#pragma once

// Legacy flags
#define SK_IGNORE_GPU_DITHER
#define SK_SUPPORT_DEPRECATED_CLIPOPS
#define SK_SUPPORT_LEGACY_DRAWLOOPER

// Needed until we fix https://bug.skia.org/2440
#define SK_SUPPORT_LEGACY_CLIPTOLAYERFLAG
#define SK_SUPPORT_LEGACY_EMBOSSMASKFILTER
#define SK_SUPPORT_LEGACY_AA_CHOICE
#define SK_SUPPORT_LEGACY_AAA_CHOICE

#define SK_DISABLE_DAA  // skbug.com/6886

#define SKIA_DLL
#define SK_ALLOW_STATIC_GLOBAL_INITIALIZERS 0
#define SK_ENABLE_DISCRETE_GPU
#define SK_GAMMA_APPLY_TO_A8
#define SK_SAMPLES_FOR_X
#define SK_SUPPORT_GPU 0
#define SK_HAS_PNG_LIBRARY

