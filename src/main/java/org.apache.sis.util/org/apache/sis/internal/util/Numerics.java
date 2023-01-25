/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sis.internal.util;

import java.util.Map;
import java.util.HashMap;
import java.text.Format;
import java.text.DecimalFormat;
import java.util.function.BiFunction;
import org.apache.sis.util.Debug;
import org.apache.sis.util.Static;
import org.apache.sis.util.Workaround;
import org.apache.sis.util.ComparisonMode;
import org.apache.sis.math.DecimalFunctions;
import org.apache.sis.math.MathFunctions;
import org.apache.sis.math.Statistics;

import static java.lang.Math.min;
import static java.lang.Math.max;
import static java.lang.Math.abs;
import static java.lang.Math.ulp;


/**
 * Miscellaneous utilities methods working on floating point numbers.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.3
 * @since   0.3
 * @module
 */
public final class Numerics extends Static {
    /**
     * Some frequently used {@link Double} values. As of Java 8, those values do not
     * seem to be cached by {@link Double#valueOf(double)} like JDK does for integers.
     */
    private static final Map<Object,Object> CACHE = new HashMap<>(32);
    static {
        cache(   0);
        cache(   1);
        cache(  10);
        cache(  60);
        cache(  90);
        cache( 100);
        cache( 180);
        cache( 180*60*60);
        cache( 360);
        cache(1000);
        cache(Double.POSITIVE_INFINITY);
        // Do not cache NaN values because Double.equals(Object) consider all NaN as equal.
    }

    /**
     * Helper method for the construction of the {@link #CACHE} map.
     */
    private static void cache(final double value) {
        Double boxed;
        boxed =  value; CACHE.put(boxed, boxed);
        boxed = -value; CACHE.put(boxed, boxed);
    }

    /**
     * Maximum number of rows or columns in Apache SIS matrices. We define a maximum because SIS is expected to work
     * mostly with small matrices, because their sizes are related to the number of dimensions in coordinate systems.
     * The maximum should not be greater than {@value Short#MAX_VALUE} in order to ensure that {@code rows * columns}
     * stay below the {@link Integer#MAX_VALUE} / 2 limit.
     *
     * <p>We also use this value as a limit for the number of dimensions minus 1 (we need to subtract one because of
     * the room needed for the translation column and the extra row in affine transforms).  Actually many Apache SIS
     * classes have their number of dimensions limited mostly by the capacity of the {@code int} primitive type, but
     * we nevertheless set the maximum number of dimensions to a lower value for catching probable errors. Note that
     * this is not a "universal" limit through Apache SIS, as some algorithms impose a smaller number of dimensions.
     * Some other limits found in specific Apache SIS code are 20 or {@value Long#SIZE}.</p>
     */
    public static final int MAXIMUM_MATRIX_SIZE = Short.MAX_VALUE;

    /**
     * Relative difference tolerated when comparing floating point numbers using
     * {@link org.apache.sis.util.ComparisonMode#APPROXIMATE}.
     *
     * <p>Historically, this was the relative tolerance threshold for considering two
     * matrices as equal. This value has been determined empirically in order to allow
     * {@code org.apache.sis.referencing.operation.transform.ConcatenatedTransform} to
     * detect the cases where two {@link org.apache.sis.referencing.operation.transform.LinearTransform}
     * are equal for practical purpose. This threshold can be used as below:</p>
     *
     * {@preformat java
     *     Matrix m1 = ...;
     *     Matrix m2 = ...;
     *     if (Matrices.epsilonEqual(m1, m2, COMPARISON_THRESHOLD, true)) {
     *         // Consider that matrixes are equal.
     *     }
     * }
     *
     * By extension, the same threshold value is used for comparing other floating point values.
     *
     * <p>The current value is set to the smallest power of 10 which allow the
     * {@code org.apache.sis.test.integration.ConsistencyTest} to pass.</p>
     *
     * @see org.apache.sis.internal.referencing.Formulas#LINEAR_TOLERANCE
     * @see org.apache.sis.internal.referencing.Formulas#ANGULAR_TOLERANCE
     */
    public static final double COMPARISON_THRESHOLD = 1E-13;

    /**
     * Bit mask to isolate the sign bit of non-{@linkplain Double#isNaN(double) NaN} values in a
     * {@code double}. For any real value, the following code evaluate to 0 if the given value is
     * positive:
     *
     * {@preformat java
     *     Double.doubleToRawLongBits(value) & SIGN_BIT_MASK;
     * }
     *
     * Note that this idiom differentiates positive zero from negative zero.
     * It should be used only when such difference matter.
     *
     * @see org.apache.sis.math.MathFunctions#isPositive(double)
     * @see org.apache.sis.math.MathFunctions#isNegative(double)
     * @see org.apache.sis.math.MathFunctions#isSameSign(double, double)
     * @see org.apache.sis.math.MathFunctions#xorSign(double, double)
     */
    public static final long SIGN_BIT_MASK = Long.MIN_VALUE;

    /**
     * Number of bits in the significand (mantissa) part of IEEE 754 {@code double} representation,
     * <strong>not</strong> including the hidden bit.
     */
    public static final int SIGNIFICAND_SIZE = 52;

    /**
     * Number of bits in the significand (mantissa) part of IEEE 754 {@code float} representation,
     * <strong>not</strong> including the hidden bit.
     */
    public static final int SIGNIFICAND_SIZE_OF_FLOAT = 23;

    /**
     * Maximal integer value which is convertible to {@code float} type without lost of precision digits.
     *
     * @since 1.1
     */
    public static final int MAX_INTEGER_CONVERTIBLE_TO_FLOAT = 1 << (SIGNIFICAND_SIZE_OF_FLOAT + 1);

    /**
     * Right shift to apply for a result equivalent to a division by {@Long#SIZE} (ignoring negative numbers).
     * The value is {@value} so that the following relationship hold: 2⁶ = {@value Long#SIZE}.
     *
     * <h4>Usage</h4>
     * The {@code x / Long.SIZE} operation can be replaced by {@code x >>> LONG_SHIFT} if <var>x</var> is positive.
     * The compiler may not do this optimization itself because those two operations are not equivalent for negative
     * <var>x</var> values (even with {@code >>} instead of {@code >>>}). By contrast it is not worth to apply such
     * replacement on multiplications because the {@code x * Long.SIZE} and {@code x << LONG_SHIFT} operations are
     * equivalent for all numbers (positive or negative), so the compiler is more likely to optimize itself.
     */
    public static final int LONG_SHIFT = 6;

    /**
     * Do not allow instantiation of this class.
     */
    private Numerics() {
    }

    /**
     * Returns a mask with the given bit set. The bit should be a number from 0 inclusive to {@value Long#SIZE} exclusive.
     * If the given bit is outside that range, then this method returns 0. The latter condition is the main difference with
     * the {@code 1L << bit} operation since {@code 1L << 64} computes 1. By contrast, {@code bitmask(64)} returns 0.
     *
     * <p>This method is invoked in contexts where we really need value 0 for a {@code bit} value of {@value Long#SIZE}.
     * For example if we want to compute the maximal value of an unsigned integer of the given number of bits, we can use
     * {@code bitmask(n) - 1}. If <var>n</var> = 64, then {@code bitmask(64) - 1} = -1 which is the desired value (the
     * signed value -1 has the same bits pattern than the maximal possible value in unsigned integer representation).</p>
     *
     * @param  bit  the bit to set.
     * @return a mask with the given bit set, or 0 if the given argument is negative or ≥ {@value Long#SIZE}.
     */
    public static long bitmask(final int bit) {
        return (bit & ~(Long.SIZE - 1)) == 0 ? (1L << bit) : 0;
    }

    /**
     * Returns {@code true} if the given number is an integer value.
     * Special cases:
     *
     * <ul>
     *   <li>If the given value is NaN, than this method returns {@code false}.</li>
     *   <li>If the given value is positive or negative infinity, then this method returns {@code true}
     *       (should be false, but this method does not check for infinities for performance reasons).</li>
     * </ul>
     *
     * @param  x the value to test.
     * @return whether the given value is an integer.
     */
    public static boolean isInteger(final double x) {
        return x == Math.rint(x);       // `rint` is reported faster than `floor`.
    }

    /**
     * Returns the smallest (closest to negative infinity) integer value that is greater than or equals to x/y.
     *
     * @param  x  the dividend.
     * @param  y  the divisor.
     * @return x/y rounded toward positive infinity.
     *
     * @see Math#floorDiv(int, int)
     */
    public static int ceilDiv(final int x, final int y) {
        int r = x / y;
        if ((x ^ y) >= 0 && (r * y != x)) {
            r++;
        }
        return r;
    }

    /**
     * Returns the smallest (closest to negative infinity) long value that is greater than or equals to x/y.
     *
     * @param  x  the dividend.
     * @param  y  the divisor.
     * @return x/y rounded toward positive infinity.
     *
     * @see Math#floorDiv(long, long)
     */
    public static long ceilDiv(final long x, final long y) {
        long r = x / y;
        if ((x ^ y) >= 0 && (r * y != x)) {
            r++;
        }
        return r;
    }

    /**
     * Returns x/y with the requirement that the division must be integer.
     *
     * @param  x  the dividend.
     * @param  y  the divisor.
     * @return x/y.
     * @throws ArithmeticException if y is zero of if the result of x/y is not an integer.
     */
    public static int wholeDiv(final int x, final int y) {
        if ((x % y) != 0) throw new ArithmeticException(x + " % " + y + " ≠ 0");
        return x / y;       // TODO: use Math.divideExact with JDK18.
    }

    /**
     * Returns {@code value} × {@code multiplier} / {@code divisor} with control against overflow.
     * The result is rounded toward zero.
     *
     * @param  value       the value to multiply and divide.
     * @param  multiplier  the multiplication factor.
     * @param  divisor     the division to apply after multiplication.
     * @return {@code value} × {@code multiplier} / {@code divisor} rounded toward zero.
     */
    public static long multiplyDivide(final long value, final long multiplier, final long divisor) {
        // TODO: uncomment with JDK9
//      final long high = Math.multiplyHigh(value, multiplier);
//      return Math.multiplyExact(value * multiplier / divisor, high);
        return Math.multiplyExact(value, multiplier) / divisor;
    }

    /**
     * Returns {@code x+y} with saturation if the result overflows long capacity.
     * This is <cite>saturation arithmetic</cite>.
     *
     * @param  x  the value for which to add something.
     * @param  y  the value to add to {@code x}.
     * @return {@code x+y} computed with saturation arithmetic.
     */
    public static long saturatingAdd(final long x, final int y) {
        final long result = x + y;
        if (y >= 0) {
            if (result < x) return Long.MAX_VALUE;
        } else {
            if (result > x) return Long.MIN_VALUE;
        }
        return result;
    }

    /**
     * Returns {@code x-y} with saturation if the result overflows long capacity.
     * This is <cite>saturation arithmetic</cite>.
     *
     * @param  x  the value for which to add something.
     * @param  y  the value to subtract from {@code x}.
     * @return {@code x-y} computed with saturation arithmetic.
     */
    public static long saturatingSubtract(final long x, final int y) {
        final long result = x - y;
        if (y < 0) {
            if (result < x) return Long.MAX_VALUE;
        } else {
            if (result > x) return Long.MIN_VALUE;
        }
        return result;
    }

    /**
     * Returns the given value clamped to the range on 32 bits integer.
     *
     * @param  value  the value to clamp.
     * @return the value clamped to the range of 32 bits integer.
     */
    public static int clamp(final long value) {
        if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) value;
    }

    /**
     * If the given value is presents in the cache, returns the cached value.
     * Otherwise returns the given value as-is.
     *
     * @param  <T>    the type of the given value.
     * @param  value  the given value for which to get a cached instance, if one exists.
     * @return an object equals to the given value (may be the given instance itself).
     */
    @SuppressWarnings("unchecked")
    public static <T> T cached(final T value) {
        return (T) CACHE.getOrDefault(value, value);
    }

    /**
     * Wraps the given {@code value} in a {@link Double} wrapper, using one of the cached instance if possible.
     *
     * @param  value  the value to get as a {@code Double}.
     * @return the given value as a {@code Double}.
     */
    public static Double valueOf(final double value) {
        final Double boxed = value;
        return (Double) CACHE.getOrDefault(boxed, boxed);
    }

    /**
     * Returns {@code true} if the given floats are equals. Positive and negative zero are
     * considered different, while a NaN value is considered equal to all other NaN values.
     *
     * @param  v1  the first value to compare.
     * @param  v2  the second value to compare.
     * @return {@code true} if both values are equal.
     *
     * @see Float#equals(Object)
     */
    public static boolean equals(final float v1, final float v2) {
        return Float.floatToIntBits(v1) == Float.floatToIntBits(v2);
    }

    /**
     * Returns {@code true} if the given doubles are equal.
     * Positive and negative zeros are considered different.
     * NaN values are considered equal to all other NaN values.
     *
     * @param  v1  the first value to compare.
     * @param  v2  the second value to compare.
     * @return {@code true} if both values are equal.
     *
     * @see Double#equals(Object)
     */
    public static boolean equals(final double v1, final double v2) {
        return Double.doubleToLongBits(v1) == Double.doubleToLongBits(v2);
    }

    /**
     * Returns {@code true} if the given doubles are equal, ignoring the sign of zero values.
     * NaN values are considered equal to all other NaN values.
     *
     * @param  v1  the first value to compare.
     * @param  v2  the second value to compare.
     * @return {@code true} if both values are equal.
     */
    public static boolean equalsIgnoreZeroSign(final double v1, final double v2) {
        return (v1 == v2) || Double.doubleToLongBits(v1) == Double.doubleToLongBits(v2);
    }

    /**
     * Returns {@code true} if the given values are approximately equal, up to the given comparison threshold.
     *
     * @param  v1  the first value to compare.
     * @param  v2  the second value to compare.
     * @param  threshold  the comparison threshold.
     * @return {@code true} if both values are approximately equal.
     */
    public static boolean epsilonEqual(final double v1, final double v2, final double threshold) {
        return (abs(v1 - v2) <= threshold) || equals(v1, v2);
    }

    /**
     * Returns {@code true} if the given values are approximately equal given the comparison mode.
     * In mode {@code APPROXIMATE} or {@code DEBUG}, this method will compute a relative comparison
     * threshold from the {@link #COMPARISON_THRESHOLD} constant.
     *
     * <p>This method does not thrown {@link AssertionError} in {@link ComparisonMode#DEBUG}.
     * It is caller responsibility to handle the {@code DEBUG} case.</p>
     *
     * @param  v1    the first value to compare.
     * @param  v2    the second value to compare.
     * @param  mode  the comparison mode to use for comparing the numbers.
     * @return {@code true} if both values are considered equal for the given comparison mode.
     */
    public static boolean epsilonEqual(final double v1, final double v2, final ComparisonMode mode) {
        if (mode.isApproximate()) {
            final double mg = max(abs(v1), abs(v2));
            if (mg != Double.POSITIVE_INFINITY) {
                return epsilonEqual(v1, v2, COMPARISON_THRESHOLD * mg);
            }
        }
        return equals(v1, v2);
    }

    /**
     * Creates a messages to put in {@link AssertionError} when two values differ in an unexpected way.
     * This is a helper method for debugging purpose only, typically used with {@code assert} statements.
     *
     * @param  name  the name of the property which differ, or {@code null} if unknown.
     * @param  v1    the first value.
     * @param  v2    the second value.
     * @return the message to put in {@code AssertionError}.
     *
     * @since 0.6
     */
    @Debug
    public static String messageForDifference(final String name, final double v1, final double v2) {
        final StringBuilder builder = new StringBuilder();
        if (name != null) {
            builder.append(name).append(": ");
        }
        builder.append("values ").append(v1).append(" and ").append(v2).append(" differ");
        final float delta = (float) abs(v1 - v2);
        if (delta < Float.POSITIVE_INFINITY) {
            builder.append(" by ").append(delta);
        }
        return builder.toString();
    }

    /**
     * Converts an unsigned {@code long} to a {@code float} value.
     *
     * @param  value  the unsigned {@code long} value.
     * @return the given unsigned {@code long} as a {@code float} value.
     *
     * @since 0.8
     */
    public static float toUnsignedFloat(final long value) {
        if (value >= 0) {
            return value;
        } else {
            // Following hack is inefficient, but should rarely be needed.
            return Float.parseFloat(Long.toUnsignedString(value));
        }
    }

    /**
     * Converts an unsigned {@code long} to a {@code double} value.
     *
     * @param  value  the unsigned {@code long} value.
     * @return the given unsigned {@code long} as a {@code double} value.
     *
     * @since 0.8
     */
    public static double toUnsignedDouble(final long value) {
        if (value >= 0) {
            return value;
        } else {
            // Following hack is inefficient, but should rarely be needed.
            return Double.parseDouble(Long.toUnsignedString(value));
        }
    }

    /**
     * Converts a power of 2 to a power of 10, rounded toward negative infinity.
     * This method is equivalent to the following code, but using only integer arithmetic:
     *
     * {@preformat java
     *     return (int) Math.floor(exp2 * LOG10_2);
     * }
     *
     * This method is valid only for arguments in the [-2620 … 2620] range, which is more than enough
     * for the range of {@code double} exponents. We do not put this method in public API because it
     * does not check the argument validity.
     *
     * <h4>Arithmetic notes</h4>
     * {@code toExp10(getExponent(10ⁿ))} returns <var>n</var> only for {@code n == 0}, and <var>n</var>-1 in all other
     * cases. This is because 10ⁿ == m × 2<sup>exp2</sup> where the <var>m</var> significand is always greater than 1,
     * which must be compensated by a smaller {@code exp2} value such as {@code toExp10(exp2) < n}. Note that if the
     * {@code getExponent(…)} argument is not a power of 10, then the result can be either <var>n</var> or <var>n</var>-1.
     *
     * @param  exp2  the power of 2 to convert Must be in the [-2620 … 2620] range.
     * @return the power of 10, rounded toward negative infinity.
     *
     * @see org.apache.sis.math.MathFunctions#LOG10_2
     * @see org.apache.sis.math.MathFunctions#getExponent(double)
     */
    public static int toExp10(final int exp2) {
        /*
         * Compute:
         *          exp2 × (log10(2) × 2ⁿ) / 2ⁿ
         * where:
         *          n = 20   (arbitrary value)
         *
         * log10(2) × 2ⁿ  =  315652.82873335475, which we round to 315653.
         *
         * The range of valid values for such approximation is determined
         * empirically by running the NumericsTest.testToExp10() method.
         */
        assert exp2 >= -2620 && exp2 <= 2620 : exp2;
        return (exp2 * 315653) >> 20;
    }

    /**
     * Returns the significand <var>m</var> of the given value such as {@code value = m×2ⁿ}
     * where <var>n</var> is {@link Math#getExponent(double)} - {@value #SIGNIFICAND_SIZE}.
     * For any non-NaN values (including infinity), the following relationship holds:
     *
     * {@preformat java
     *    assert Math.scalb(getSignificand(value), Math.getExponent(value) - SIGNIFICAND_SIZE) == Math.abs(value);
     * }
     *
     * For negative values, this method behaves as if the value was positive.
     *
     * @param  value  the value for which to get the significand.
     * @return the significand of the given value.
     */
    public static long getSignificand(final double value) {
        long bits = Double.doubleToRawLongBits(value);
        final long exponent = bits & (0x7FFL << SIGNIFICAND_SIZE);
        bits &= (1L << SIGNIFICAND_SIZE) - 1;
        if (exponent != 0) {
            bits |= (1L << SIGNIFICAND_SIZE);
        } else {
            /*
             * Sub-normal value: compensate for the fact that Math.getExponent(value) returns
             * Double.MIN_EXPONENT - 1 in this case, while we would need Double.MIN_EXPONENT.
             */
            bits <<= 1;
        }
        return bits;
    }

    /**
     * Returns the significand <var>m</var> of the given value such as {@code value = m×2ⁿ} where
     * <var>n</var> is {@link Math#getExponent(float)} - {@value #SIGNIFICAND_SIZE_OF_FLOAT}.
     * For any non-NaN positive values (including infinity), the following relationship holds:
     *
     * {@preformat java
     *    assert Math.scalb(getSignificand(value), Math.getExponent(value) - SIGNIFICAND_SIZE_OF_FLOAT) == value;
     * }
     *
     * For negative values, this method behaves as if the value was positive.
     *
     * @param  value  the value for which to get the significand.
     * @return the significand of the given value.
     */
    public static int getSignificand(final float value) {
        int bits = Float.floatToRawIntBits(value);
        final int exponent = bits & (0xFF << SIGNIFICAND_SIZE_OF_FLOAT);
        bits &= (1L << SIGNIFICAND_SIZE_OF_FLOAT) - 1;
        if (exponent != 0) {
            bits |= (1L << SIGNIFICAND_SIZE_OF_FLOAT);
        } else {
            bits <<= 1;
        }
        return bits;
    }

    /**
     * Suggests an amount of fraction digits for formatting in base 10 numbers of the given accuracy.
     * This method uses heuristic rules that may change in any future SIS version:
     *
     * <ul>
     *   <li>This method returns zero for {@link Double#NaN} of infinities.</li>
     *   <li>This method arbitrarily returns zero for 0. This is different than
     *       {@link DecimalFunctions#fractionDigitsForDelta(double, boolean)},
     *       which returns 324 (maximal numbers of fraction digits an IEEE 754 may have).</li>
     *   <li>An arbitrary limit is set to 16 digits, which is the number of digits for {@code Math.ulp(1.0)}}.</li>
     * </ul>
     *
     * This method can be used for string representations that are not controlled by the user. If instead the
     * precision is specified by users, then {@link DecimalFunctions#fractionDigitsForDelta(double, boolean)}
     * should be used instead in order to honor the user request exactly as specified.
     *
     * @param  ulp  the accuracy.
     * @return suggested amount of fraction digits for the given precision. Always positive.
     *
     * @see DecimalFunctions#fractionDigitsForDelta(double, boolean)
     *
     * @since 1.0
     */
    public static int fractionDigitsForDelta(final double ulp) {
        return (ulp != 0) ? Math.max(0, Math.min(16, DecimalFunctions.fractionDigitsForDelta(ulp, false))) : 0;
    }

    /**
     * Suggests an amount of fraction digits for the given values, ignoring NaN and infinities.
     * This method uses heuristic rules that may change in any future SIS version.
     * Current implementation returns a value which avoid printing "garbage" digits
     * with highest numbers, at the cost of loosing significant digits on smallest numbers.
     * An arbitrary limit is set to 16 digits, which is the number of digits for {@code Math.ulp(1.0)}}.
     *
     * @param  values  the values for which to get suggested amount of fraction digits.
     * @return suggested amount of fraction digits for the given values. Always positive.
     *
     * @since 1.0
     */
    public static int suggestFractionDigits(final double... values) {
        double ulp = 0;
        if (values != null) {
            for (final double v : values) {
                final double e = Math.ulp(v);
                if (e > ulp && e != Double.POSITIVE_INFINITY) {
                    ulp = e;
                }
            }
        }
        return fractionDigitsForDelta(ulp);
    }

    /**
     * Suggests an amount of fraction digits for data having the given statistics.
     * This method uses heuristic rules that may be modified in any future SIS version.
     *
     * @param  stats  statistics on the data to format.
     * @return number of fraction digits suggested. May be negative.
     *
     * @since 1.0
     */
    public static int suggestFractionDigits(final Statistics stats) {
        final double minimum = stats.minimum();
        final double maximum = stats.maximum();
        double delta = stats.standardDeviation(true);                       // 'true' is for avoiding NaN when count = 1.
        if (delta == 0) {
            delta = stats.span();                                           // May happen that the span is very narrow.
            if (delta == 0) {
                delta = abs(maximum) / 1E+6;                                // The 1E+6 factor is arbitrary.
            }
        } else {
            /*
             * Computes a representative range of values. We take 2 standard deviations away
             * from the mean. Assuming that data have a gaussian distribution, this is 97.7%
             * of data. If the data have a uniform distribution, then this is 100% of data.
             */
            final double mean = stats.mean();
            delta *= 2;
            delta  = min(maximum, mean+delta) - max(minimum, mean-delta);   // Range of 97.7% of values.
            delta /= min(stats.count() * 1E+2, 1E+6);                       // Mean delta for uniform distribution + 2 decimal digits.
            delta  = max(delta, max(ulp(minimum), ulp(maximum)));           // Not finer than 'double' accuracy.
        }
        return fractionDigitsForDelta(delta);
    }

    /**
     * Formats the given value with the given format, using scientific notation if needed.
     * This is a workaround for {@link DecimalFormat} not switching automatically to scientific notation for large numbers.
     *
     * @param  format  the format to use for formatting the given value.
     * @param  value   the value to format.
     * @param  action  the method to invoke. Typically {@code Format::format}.
     * @return the result of {@code action}.
     */
    @Workaround(library="JDK", version="10")
    public static String useScientificNotationIfNeeded(final Format format, final Object value, final BiFunction<Format,Object,String> action) {
        if (value instanceof Number && format instanceof DecimalFormat) {
            final DecimalFormat df = (DecimalFormat) format;
            final int maxFD = df.getMaximumFractionDigits();
            final double m = abs(((Number) value).doubleValue());
            if (m > 0 && (m >= 1E+9 || m < MathFunctions.pow10(-Math.min(maxFD, 6)))) {
                final int minFD = df.getMinimumFractionDigits();
                final String pattern = df.toPattern();
                try {
                    df.applyPattern("0.######E00");
                    if (maxFD > 0) {
                        df.setMinimumFractionDigits(minFD);
                        df.setMaximumFractionDigits(maxFD);
                    }
                    return action.apply(format, value);
                } finally {
                    df.applyPattern(pattern);
                }
            }
        }
        return action.apply(format, value);
    }
}
