package com.muhardin.endy.training.billing;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

public class BitmapCalculationTests {

    @Test
    public void testCalculateBitmap(){
        BigInteger bitmap = BigInteger.ZERO;
        bitmap = bitmap.setBit(128 - 1);
        bitmap = bitmap.setBit(128 - 2);
        bitmap = bitmap.setBit(128 - 3);
        bitmap = bitmap.setBit(128 - 4);
        bitmap = bitmap.setBit(128 - 7);
        bitmap = bitmap.setBit(128 - 39);
        bitmap = bitmap.setBit(128 - 70);
        
        System.out
              .println(bitmap.toString(16));
    }

    @Test
    public void testParseBitmap(){
        String hexBitmap = "F238E48128E090340000000000000020";
        BigInteger bitmap = new BigInteger(hexBitmap, 16);
        
        for(int i=0; i<128; i++){
            if(bitmap.testBit(128 - i - 1)){
                System.out.println("Field " + (i + 1) + " is present");
            } else {
                System.out.println("Field " + (i + 1) + " is absent");
            }
        }
    }
}
