/**
 * HistogramTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.util.zip.Deflater;

/**
 * JUnit test for {@link Histogram}
 */
public class DoubleHistogramTest {
    static final long trackableValueRangeSize = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units
    static final int numberOfSignificantValueDigits = 3;
    // static final long testValueLevel = 12340;
    static final double testValueLevel = 4.0;

    @Test
    public void testConstructionArgumentRanges() throws Exception {
        Boolean thrown = false;
        DoubleHistogram histogram = null;

        try {
            // This should throw:
            histogram = new DoubleHistogram(1, numberOfSignificantValueDigits);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
        Assert.assertEquals(histogram, null);

        thrown = false;
        try {
            // This should throw:
            histogram = new DoubleHistogram(trackableValueRangeSize, 6);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
        Assert.assertEquals(histogram, null);

        thrown = false;
        try {
            // This should throw:
            histogram = new DoubleHistogram(trackableValueRangeSize, -1);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
        Assert.assertEquals(histogram, null);
    }

    @Test
    public void testConstructionArgumentGets() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        // Record 1.0, and verify that the range adjust to it:
        histogram.recordValue(Math.pow(2.0, 20));
        histogram.recordValue(1.0);
        Assert.assertEquals(1.0, histogram.getCurrentLowestTrackableNonZeroValue(), 0.001);
        Assert.assertEquals(trackableValueRangeSize, histogram.getHighestToLowestValueRatio(), 0.001);
        Assert.assertEquals(numberOfSignificantValueDigits, histogram.getNumberOfSignificantValueDigits(), 0.001);

        DoubleHistogram histogram2 = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        // Record a larger value, and verify that the range adjust to it too:
        histogram2.recordValue(2048.0 * 1024.0 * 1024.0);
        Assert.assertEquals(2048.0 * 1024.0 * 1024.0, histogram2.getCurrentLowestTrackableNonZeroValue(), 0.001);

        DoubleHistogram histogram3 = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        // Record a value that is 1000x outside of the initially set range, which should scale us by 1/1024x:
        histogram3.recordValue(1/1000.0);
        Assert.assertEquals(1.0/1024, histogram3.getCurrentLowestTrackableNonZeroValue(), 0.001);
    }

    @Test
    public void testDataRange() {
        // A trackableValueRangeSize histigram
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(0.0);  // Include a zero value to make sure things are handled right.
        Assert.assertEquals(1L, histogram.getCountAtValue(0.0));

        double topValue = 1.0;
        try {
            while (true) {
                histogram.recordValue(topValue);
                topValue *= 2.0;
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
        }
        Assert.assertEquals((double) (1L << 33), topValue, 0.00001);
        Assert.assertEquals(1L, histogram.getCountAtValue(0.0));

        histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(0.0); // Include a zero value to make sure things are handled right.

        double bottomValue = (double) (1L << 33);
        try {
            while (true) {
                histogram.recordValue(bottomValue);
                bottomValue /= 2.0;
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
        }
        Assert.assertEquals(1.0, bottomValue, 0.00001);

        long expectedRange = 1L << (findContainingBinaryOrderOfMagnitude(trackableValueRangeSize) + 1);
        Assert.assertEquals(expectedRange, (topValue/ bottomValue), 0.00001);
        Assert.assertEquals(1L, histogram.getCountAtValue(0.0));
    }

    @Test
    public void testRecordValue() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        Assert.assertEquals(1L, histogram.getCountAtValue(testValueLevel));
        Assert.assertEquals(1L, histogram.getTotalCount());
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testRecordValue_Overflow_ShouldThrowException() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(trackableValueRangeSize * 3);
        histogram.recordValue(1.0);
    }

    @Test
    public void testRecordValueWithExpectedInterval() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(0);
        histogram.recordValueWithExpectedInterval(testValueLevel, testValueLevel/4);
        DoubleHistogram rawHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        rawHistogram.recordValue(0);
        rawHistogram.recordValue(testValueLevel);
        // The raw data will not include corrected samples:
        Assert.assertEquals(1L, rawHistogram.getCountAtValue(0));
        Assert.assertEquals(0L, rawHistogram.getCountAtValue((testValueLevel * 1 )/4));
        Assert.assertEquals(0L, rawHistogram.getCountAtValue((testValueLevel * 2 )/4));
        Assert.assertEquals(0L, rawHistogram.getCountAtValue((testValueLevel * 3 )/4));
        Assert.assertEquals(1L, rawHistogram.getCountAtValue((testValueLevel * 4 )/4));
        Assert.assertEquals(2L, rawHistogram.getTotalCount());
        // The data will include corrected samples:
        Assert.assertEquals(1L, histogram.getCountAtValue(0));
        Assert.assertEquals(1L, histogram.getCountAtValue((testValueLevel * 1 )/4));
        Assert.assertEquals(1L, histogram.getCountAtValue((testValueLevel * 2 )/4));
        Assert.assertEquals(1L, histogram.getCountAtValue((testValueLevel * 3 )/4));
        Assert.assertEquals(1L, histogram.getCountAtValue((testValueLevel * 4 )/4));
        Assert.assertEquals(5L, histogram.getTotalCount());
    }

    @Test
    public void testReset() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.reset();
        Assert.assertEquals(0L, histogram.getCountAtValue(testValueLevel));
        Assert.assertEquals(0L, histogram.getTotalCount());
    }

    @Test
    public void testAdd() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        DoubleHistogram other = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);

        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 1000);
        other.recordValue(testValueLevel);
        other.recordValue(testValueLevel * 1000);
        histogram.add(other);
        Assert.assertEquals(2L, histogram.getCountAtValue(testValueLevel));
        Assert.assertEquals(2L, histogram.getCountAtValue(testValueLevel * 1000));
        Assert.assertEquals(4L, histogram.getTotalCount());

        DoubleHistogram biggerOther = new DoubleHistogram(trackableValueRangeSize * 2, numberOfSignificantValueDigits);
        biggerOther.recordValue(testValueLevel);
        biggerOther.recordValue(testValueLevel * 1000);

        // Adding the smaller histogram to the bigger one should work:
        biggerOther.add(histogram);
        Assert.assertEquals(3L, biggerOther.getCountAtValue(testValueLevel));
        Assert.assertEquals(3L, biggerOther.getCountAtValue(testValueLevel * 1000));
        Assert.assertEquals(6L, biggerOther.getTotalCount());

        // Since we are auto-sized, trying to add a larger histogram into a smaller one should work if no
        // overflowing data is there:
        boolean thrown = false;
        try {
            // This should throw:
            histogram.add(biggerOther);
        } catch (ArrayIndexOutOfBoundsException e) {
            thrown = true;
        }
        Assert.assertFalse(thrown);

        // But trying to add smaller values to a larger histogram that actually uses it's range should throw an AIOOB:
        histogram.recordValue(1.0);
        other.recordValue(1.0);
        biggerOther.recordValue(trackableValueRangeSize * 8);

        thrown = false;
        try {
            // This should throw:
            biggerOther.add(histogram);
        } catch (ArrayIndexOutOfBoundsException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }


    @Test
    public void testSizeOfEquivalentValueRange() {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(1.0);
        Assert.assertEquals("Size of equivalent range for value 1 is 1",
                1.0/1024.0, histogram.sizeOfEquivalentValueRange(1), 0.001);
        Assert.assertEquals("Size of equivalent range for value 2500 is 2",
                2, histogram.sizeOfEquivalentValueRange(2500), 0.001);
        Assert.assertEquals("Size of equivalent range for value 8191 is 4",
                4, histogram.sizeOfEquivalentValueRange(8191), 0.001);
        Assert.assertEquals("Size of equivalent range for value 8192 is 8",
                8, histogram.sizeOfEquivalentValueRange(8192), 0.001);
        Assert.assertEquals("Size of equivalent range for value 10000 is 8",
                8, histogram.sizeOfEquivalentValueRange(10000), 0.001);
    }

    @Test
    public void testLowestEquivalentValue() {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(1.0);
        Assert.assertEquals("The lowest equivalent value to 10007 is 10000",
                10000, histogram.lowestEquivalentValue(10007), 0.001);
        Assert.assertEquals("The lowest equivalent value to 10009 is 10008",
                10008, histogram.lowestEquivalentValue(10009), 0.001);
    }

    @Test
    public void testHighestEquivalentValue() {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(1.0);
        Assert.assertEquals("The highest equivalent value to 8180 is 8183",
                8183.99999, histogram.highestEquivalentValue(8180), 0.001);
        Assert.assertEquals("The highest equivalent value to 8187 is 8191",
                8191.99999, histogram.highestEquivalentValue(8191), 0.001);
        Assert.assertEquals("The highest equivalent value to 8193 is 8199",
                8199.99999, histogram.highestEquivalentValue(8193), 0.001);
        Assert.assertEquals("The highest equivalent value to 9995 is 9999",
                9999.99999, histogram.highestEquivalentValue(9995), 0.001);
        Assert.assertEquals("The highest equivalent value to 10007 is 10007",
                10007.99999, histogram.highestEquivalentValue(10007), 0.001);
        Assert.assertEquals("The highest equivalent value to 10008 is 10015",
                10015.99999, histogram.highestEquivalentValue(10008), 0.001);
    }

    @Test
    public void testMedianEquivalentValue() {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(1.0);
        Assert.assertEquals("The median equivalent value to 4 is 4",
                4.002, histogram.medianEquivalentValue(4), 0.001);
        Assert.assertEquals("The median equivalent value to 5 is 5",
                5.002, histogram.medianEquivalentValue(5), 0.001);
        Assert.assertEquals("The median equivalent value to 4000 is 4001",
                4001, histogram.medianEquivalentValue(4000), 0.001);
        Assert.assertEquals("The median equivalent value to 8000 is 8002",
                8002, histogram.medianEquivalentValue(8000), 0.001);
        Assert.assertEquals("The median equivalent value to 10007 is 10004",
                10004, histogram.medianEquivalentValue(10007), 0.001);
    }

    @Test
    public void testNextNonEquivalentValue() {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        Assert.assertNotSame(null, histogram);
    }

    void testDoubleHistogramSerialization(DoubleHistogram histogram) throws Exception {
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValueWithExpectedInterval(histogram.getCurrentHighestTrackableValue() - 1, histogram.getCurrentHighestTrackableValue() / 1000);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        ByteArrayInputStream bis = null;
        ObjectInput in = null;
        DoubleHistogram newHistogram = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(histogram);
            Deflater compresser = new Deflater();
            compresser.setInput(bos.toByteArray());
            compresser.finish();
            byte [] compressedOutput = new byte[1024*1024];
            int compressedDataLength = compresser.deflate(compressedOutput);
            System.out.println("Serialized form of " + histogram.getClass() + " with internalHighestToLowestValueRatio = " +
                    histogram.getHighestToLowestValueRatio() + "\n and a numberOfSignificantValueDigits = " +
                    histogram.getNumberOfSignificantValueDigits() + " is " + bos.toByteArray().length +
                    " bytes long. Compressed form is " + compressedDataLength + " bytes long.");
            System.out.println("   (estimated footprint was " + histogram.getEstimatedFootprintInBytes() + " bytes)");
            bis = new ByteArrayInputStream(bos.toByteArray());
            in = new ObjectInputStream(bis);
            newHistogram = (DoubleHistogram) in.readObject();
        } finally {
            if (out != null) out.close();
            bos.close();
            if (in !=null) in.close();
            if (bis != null) bis.close();
        }
        Assert.assertNotNull(newHistogram);
        assertEqual(histogram, newHistogram);
    }

    private void assertEqual(DoubleHistogram expectedHistogram, DoubleHistogram actualHistogram) {
        Assert.assertEquals(expectedHistogram, actualHistogram);
        Assert.assertEquals(
                expectedHistogram.getCountAtValue(testValueLevel),
                actualHistogram.getCountAtValue(testValueLevel));
        Assert.assertEquals(
                expectedHistogram.getCountAtValue(testValueLevel * 10),
                actualHistogram.getCountAtValue(testValueLevel * 10));
        Assert.assertEquals(
                expectedHistogram.getTotalCount(),
                actualHistogram.getTotalCount());
    }

    @Test
    public void testSerialization() throws Exception {
        DoubleHistogram histogram =
                new DoubleHistogram(trackableValueRangeSize, 3);
        testDoubleHistogramSerialization(histogram);
        DoubleHistogram withIntHistogram =
                new DoubleHistogram(trackableValueRangeSize, 3, IntCountsHistogram.class);
        testDoubleHistogramSerialization(withIntHistogram);
        DoubleHistogram withShortHistogram =
                new DoubleHistogram(trackableValueRangeSize, 3, ShortCountsHistogram.class);
        testDoubleHistogramSerialization(withShortHistogram);
        histogram = new DoubleHistogram(trackableValueRangeSize, 2, Histogram.class);
        testDoubleHistogramSerialization(histogram);
        withIntHistogram = new DoubleHistogram(trackableValueRangeSize, 2, IntCountsHistogram.class);
        testDoubleHistogramSerialization(withIntHistogram);
        withShortHistogram = new DoubleHistogram(trackableValueRangeSize, 2, ShortCountsHistogram.class);
        testDoubleHistogramSerialization(withShortHistogram);
    }
    
    @Test
    public void testCopy() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValueWithExpectedInterval(histogram.getCurrentHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of DoubleHistogram:");
        assertEqual(histogram, histogram.copy());

        DoubleHistogram withIntHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                IntCountsHistogram.class);
        withIntHistogram.recordValue(testValueLevel);
        withIntHistogram.recordValue(testValueLevel * 10);
        withIntHistogram.recordValueWithExpectedInterval(withIntHistogram.getCurrentHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of DoubleHistogram backed by IntHistogram:");
        assertEqual(withIntHistogram, withIntHistogram.copy());

        DoubleHistogram withShortHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                ShortCountsHistogram.class);
        withShortHistogram.recordValue(testValueLevel);
        withShortHistogram.recordValue(testValueLevel * 10);
        withShortHistogram.recordValueWithExpectedInterval(withShortHistogram.getCurrentHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of DoubleHistogram backed by ShortHistogram:");
        assertEqual(withShortHistogram, withShortHistogram.copy());

        DoubleHistogram withAtomicHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                AtomicHistogram.class);
        withAtomicHistogram.recordValue(testValueLevel);
        withAtomicHistogram.recordValue(testValueLevel * 10);
        withAtomicHistogram.recordValueWithExpectedInterval(withAtomicHistogram.getCurrentHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of DoubleHistogram backed by AtomicHistogram:");
        assertEqual(withAtomicHistogram, withAtomicHistogram.copy());

        DoubleHistogram withSyncHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                SynchronizedHistogram.class);
        withSyncHistogram.recordValue(testValueLevel);
        withSyncHistogram.recordValue(testValueLevel * 10);
        withSyncHistogram.recordValueWithExpectedInterval(withSyncHistogram.getCurrentHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of DoubleHistogram backed by SynchronizedHistogram:");
        assertEqual(withSyncHistogram, withSyncHistogram.copy());
    }

    @Test
    public void testCopyInto() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        DoubleHistogram targetHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValueWithExpectedInterval(histogram.getCurrentHighestTrackableValue() - 1,
                histogram.getCurrentHighestTrackableValue() / 1000);

        System.out.println("Testing copyInto for DoubleHistogram:");
        histogram.copyInto(targetHistogram);
        assertEqual(histogram, targetHistogram);

        histogram.recordValue(testValueLevel * 20);

        histogram.copyInto(targetHistogram);
        assertEqual(histogram, targetHistogram);


        DoubleHistogram withIntHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                IntCountsHistogram.class);
        DoubleHistogram targetWithIntHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                IntCountsHistogram.class);
        withIntHistogram.recordValue(testValueLevel);
        withIntHistogram.recordValue(testValueLevel * 10);
        withIntHistogram.recordValueWithExpectedInterval(withIntHistogram.getCurrentHighestTrackableValue() - 1,
                histogram.getCurrentHighestTrackableValue() / 1000);

        System.out.println("Testing copyInto for DoubleHistogram backed by IntHistogram:");
        withIntHistogram.copyInto(targetWithIntHistogram);
        assertEqual(withIntHistogram, targetWithIntHistogram);

        withIntHistogram.recordValue(testValueLevel * 20);

        withIntHistogram.copyInto(targetWithIntHistogram);
        assertEqual(withIntHistogram, targetWithIntHistogram);


        DoubleHistogram withShortHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                ShortCountsHistogram.class);
        DoubleHistogram targetWithShortHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                ShortCountsHistogram.class);
        withShortHistogram.recordValue(testValueLevel);
        withShortHistogram.recordValue(testValueLevel * 10);
        withShortHistogram.recordValueWithExpectedInterval(withShortHistogram.getCurrentHighestTrackableValue() - 1,
                histogram.getCurrentHighestTrackableValue() / 1000);

        System.out.println("Testing copyInto for DoubleHistogram backed by ShortHistogram:");
        withShortHistogram.copyInto(targetWithShortHistogram);
        assertEqual(withShortHistogram, targetWithShortHistogram);

        withShortHistogram.recordValue(testValueLevel * 20);

        withShortHistogram.copyInto(targetWithShortHistogram);
        assertEqual(withShortHistogram, targetWithShortHistogram);


        DoubleHistogram withAtomicHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                AtomicHistogram.class);
        DoubleHistogram targetWithAtomicHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                AtomicHistogram.class);
        withAtomicHistogram.recordValue(testValueLevel);
        withAtomicHistogram.recordValue(testValueLevel * 10);
        withAtomicHistogram.recordValueWithExpectedInterval(withAtomicHistogram.getCurrentHighestTrackableValue() - 1,
                histogram.getCurrentHighestTrackableValue() / 1000);

        System.out.println("Testing copyInto for DoubleHistogram backed by AtomicHistogram:");
        withAtomicHistogram.copyInto(targetWithAtomicHistogram);
        assertEqual(withAtomicHistogram, targetWithAtomicHistogram);

        withAtomicHistogram.recordValue(testValueLevel * 20);

        withAtomicHistogram.copyInto(targetWithAtomicHistogram);
        assertEqual(withAtomicHistogram, targetWithAtomicHistogram);


        DoubleHistogram withSyncHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                SynchronizedHistogram.class);
        DoubleHistogram targetWithSyncHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                SynchronizedHistogram.class);
        withSyncHistogram.recordValue(testValueLevel);
        withSyncHistogram.recordValue(testValueLevel * 10);
        withSyncHistogram.recordValueWithExpectedInterval(withSyncHistogram.getCurrentHighestTrackableValue() - 1,
                histogram.getCurrentHighestTrackableValue() / 1000);

        System.out.println("Testing copyInto for DoubleHistogram backed by SynchronizedHistogram:");
        withSyncHistogram.copyInto(targetWithSyncHistogram);
        assertEqual(withSyncHistogram, targetWithSyncHistogram);

        withSyncHistogram.recordValue(testValueLevel * 20);

        withSyncHistogram.copyInto(targetWithSyncHistogram);
        assertEqual(withSyncHistogram, targetWithSyncHistogram);
    }

    private int findContainingBinaryOrderOfMagnitude(double doubleNumber) {
        long longNumber = (long) Math.ceil(doubleNumber);
        int pow2ceiling = 64 - Long.numberOfLeadingZeros(longNumber); // smallest power of 2 containing value
        pow2ceiling = Math.min(pow2ceiling, 62);
        return pow2ceiling;
    }

    private int findContainingBinaryOrderOfMagnitude(long longNumber) {
        int pow2ceiling = 64 - Long.numberOfLeadingZeros(longNumber); // smallest power of 2 containing value
        pow2ceiling = Math.min(pow2ceiling, 62);
        return pow2ceiling;
    }
}
