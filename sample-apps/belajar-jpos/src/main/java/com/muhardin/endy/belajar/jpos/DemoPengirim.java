package com.muhardin.endy.belajar.jpos;

import java.io.IOException;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.channel.BASE24TCPChannel;
import org.jpos.iso.packager.BASE24Packager;

public class DemoPengirim {
    public static void main(String[] args) throws ISOException, IOException {
        BASE24Packager packager = new BASE24Packager();

        ISOMsg echoRequest = new ISOMsg("0800");
        echoRequest.set(7, "1028135200");
        echoRequest.set(70, "301");

        echoRequest.setPackager(packager);

        String strEchoRequest = new String(echoRequest.pack());
        System.out.println("Echo Request : "+strEchoRequest);
        System.out.println("Panjang message : "+strEchoRequest.length());

        BASE24TCPChannel channel = new BASE24TCPChannel("localhost", 12345, packager);
        //BASE24Channel channel = new BASE24Channel("localhost", 12345, packager);
        //ASCIIChannel channel = new ASCIIChannel("localhost", 12345, packager);

        channel.connect();
        channel.send(echoRequest);
    }
}
