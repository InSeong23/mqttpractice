// package com.nhnacademy;

// public class ScaleConverter {
// public static double convertAndScale(short[] values, String type, double
// scale) {
// long rawValue = 0;
// for (short value : values) {
// rawValue = (rawValue << 16) | (value & 0xFFFF);
// }

// switch (type.toUpperCase()) {
// case "UINT16":
// return rawValue / scale;
// case "UINT32":
// return rawValue / scale;
// case "INT16":
// return (short) rawValue / scale;
// case "INT32":
// return rawValue / scale;
// default:
// return 0;
// }
// }
// }
