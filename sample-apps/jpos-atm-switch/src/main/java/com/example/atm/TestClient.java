package com.example.atm;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.channel.ASCIIChannel;
import org.jpos.iso.packager.BASE24Packager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TestClient {
    private ASCIIChannel channel;
    private String host;
    private int port;
    private int stan = 1;

    public TestClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        channel = new ASCIIChannel(host, port, new BASE24Packager());
        channel.connect();
        System.out.println("Connected to " + host + ":" + port);
    }

    public void disconnect() throws IOException {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
            System.out.println("Disconnected from server");
        }
    }

    public ISOMsg sendBalanceInquiry(String pan, String terminalId) throws ISOException, IOException {
        ISOMsg request = new ISOMsg();
        request.setPackager(channel.getPackager());
        request.setMTI("0200");
        request.set(2, pan);
        request.set(3, "310000");
        request.set(7, getCurrentDateTime());
        request.set(11, String.format("%06d", stan++));
        request.set(12, getCurrentTime());
        request.set(13, getCurrentDate());
        request.set(41, terminalId);
        request.set(42, "MERCHANT123456");

        System.out.println("\n=== Sending Balance Inquiry ===");
        System.out.println("PAN: " + pan + ", Terminal: " + terminalId);

        channel.send(request);
        ISOMsg response = channel.receive();

        System.out.println("\n=== Received Response ===");
        System.out.println("MTI: " + response.getMTI());
        System.out.println("Response Code: " + response.getString(39));
        if (response.hasField(54)) {
            System.out.println("Balance: " + response.getString(54));
        }

        return response;
    }

    public ISOMsg sendCashWithdrawal(String pan, String terminalId, long amount) throws ISOException, IOException {
        ISOMsg request = new ISOMsg();
        request.setPackager(channel.getPackager());
        request.setMTI("0200");
        request.set(2, pan);
        request.set(3, "010000");
        request.set(4, String.format("%012d", amount));
        request.set(7, getCurrentDateTime());
        request.set(11, String.format("%06d", stan++));
        request.set(12, getCurrentTime());
        request.set(13, getCurrentDate());
        request.set(41, terminalId);
        request.set(42, "MERCHANT123456");

        System.out.println("\n=== Sending Cash Withdrawal ===");
        System.out.println("PAN: " + pan + ", Terminal: " + terminalId + ", Amount: " + (amount / 100.0));

        channel.send(request);
        ISOMsg response = channel.receive();

        System.out.println("\n=== Received Response ===");
        System.out.println("MTI: " + response.getMTI());
        System.out.println("Response Code: " + response.getString(39));
        if (response.hasField(54)) {
            System.out.println("New Balance: " + response.getString(54));
        }

        return response;
    }

    public ISOMsg sendReversal(String pan, String terminalId, String originalStan, long amount) throws ISOException, IOException {
        ISOMsg request = new ISOMsg();
        request.setPackager(channel.getPackager());
        request.setMTI("0400");
        request.set(2, pan);
        request.set(3, "010000");
        request.set(4, String.format("%012d", amount));
        request.set(7, getCurrentDateTime());
        request.set(11, String.format("%06d", stan++));
        request.set(12, getCurrentTime());
        request.set(13, getCurrentDate());
        request.set(41, terminalId);
        request.set(42, "MERCHANT123456");
        request.set(90, originalStan);

        System.out.println("\n=== Sending Reversal ===");
        System.out.println("Original STAN: " + originalStan);

        channel.send(request);
        ISOMsg response = channel.receive();

        System.out.println("\n=== Received Response ===");
        System.out.println("MTI: " + response.getMTI());
        System.out.println("Response Code: " + response.getString(39));

        return response;
    }

    private String getCurrentDateTime() {
        return new SimpleDateFormat("MMddHHmmss").format(new Date());
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("HHmmss").format(new Date());
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("MMdd").format(new Date());
    }

    public static void main(String[] args) {
        TestClient client = new TestClient("localhost", 8000);

        try {
            client.connect();

            Thread.sleep(2000);

            client.sendBalanceInquiry("4111111111111111", "ATM00001");

            Thread.sleep(1000);

            ISOMsg withdrawalResponse = client.sendCashWithdrawal("4111111111111111", "ATM00001", 50000);

            Thread.sleep(1000);

            client.sendBalanceInquiry("4111111111111111", "ATM00001");

            Thread.sleep(1000);

            String originalStan = withdrawalResponse.getString(11);
            client.sendReversal("4111111111111111", "ATM00001", originalStan, 50000);

            Thread.sleep(1000);

            client.sendBalanceInquiry("4111111111111111", "ATM00001");

            Thread.sleep(2000);

            client.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
