package com.muhardin.endy.belajar.jpos;

import java.net.ServerSocket;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.channel.BASE24TCPChannel;
import org.jpos.iso.packager.BASE24Packager;

public class DemoPenerima {
    public static void main(String[] args) throws Exception {
        BASE24Packager packager = new BASE24Packager();
        ServerSocket serverSocket = new ServerSocket(12345);
        BASE24TCPChannel channel = new BASE24TCPChannel(packager);
        channel.accept(serverSocket);

        ISOMsg echoRequest = channel.receive();
        ISOMsg echoResponse = (ISOMsg) echoRequest.clone();
        echoResponse.setMTI("0810");
        echoResponse.set(39,"00");

        System.out.println("Echo Request : "+new String(echoRequest.pack()));

        String strEchoResponse = new String(echoResponse.pack());
        System.out.println("Echo Response : "+strEchoResponse);
        System.out.println("Panjang response : "+strEchoResponse.length());

    }
}
