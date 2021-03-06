package io.stompjlambda.stream;

import io.stompjlambda.Command;
import io.stompjlambda.Frame;
import io.stompjlambda.StompException;
import io.stompjlambda.StompListener;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public class TcpStompStream implements StompStream {
    private String server;
    private int port;
    private SocketChannel socketChannel;
    private boolean connected;

    @Override
    public void connect(String server, int port) throws StompException {
        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress(server, port));
            connected = true;
        } catch (IOException e) {
            connected = false;
            String msg = String.format("Error connecting to STOMP %s:%d", server, port);
            throw new StompException(msg, e);
        }
    }

    @Override
    public Frame receive() throws StompException {
        try {
            String inFrameStr = doReceive();
            return Frame.deserialize(inFrameStr);
        } catch (IOException e) {
            throw new StompException("Error receiving STOMP from TCP.", e);
        }
    }

    private String doReceive() throws IOException {
        int bytesRead = 0;
        StringBuilder inFrameStrBldr = new StringBuilder();
        ByteBuffer inBuf;
        char lastChar = 'a';

        do {
            inBuf = ByteBuffer.allocate(1024);
            bytesRead = socketChannel.read(inBuf);

            if (bytesRead > 0) {
                // NULL character '\0' may not be the last one.
                byte[] bytes = Arrays.copyOf(inBuf.array(), bytesRead);
                int nullPos = findNullChar(bytes, bytesRead);
                bytes = (nullPos == -1) ? bytes : Arrays.copyOf(bytes, nullPos + 1);
                inFrameStrBldr.append(new String(bytes, "UTF-8"));
                lastChar = (nullPos == -1) ? inFrameStrBldr.charAt(inFrameStrBldr.length() - 1) : inFrameStrBldr.charAt(nullPos);
            }
        } while (lastChar != '\0');

        String inFrameStr = inFrameStrBldr.toString();
        return inFrameStr;
    }

    private int findNullChar(byte[] bytes, int before) {
        int nullPos = before - 1;
        for (; nullPos >= 0; nullPos--) {
            if (bytes[nullPos] == '\0')
                break;
        }
        return nullPos;
    }

    @Override
    public void send(Frame frame) throws StompException {
        try {
            String connectFrameSerialized = frame.serialize();
            ByteBuffer outBuf = ByteBuffer.allocate(connectFrameSerialized.length());
            outBuf.clear();

            outBuf.put(connectFrameSerialized.getBytes("UTF-8"));

            outBuf.flip();
            while (outBuf.hasRemaining()) {
                socketChannel.write(outBuf);
            }
        } catch (IOException e) {
            String msg = String.format("Error sending Frame: %s", frame);
            throw new StompException(msg, e);
        }
    }

    @Override
    public void setListener(StompListener listener) {

    }

    @Override
    public void disconnect() throws StompException {
        try {
            connected = false;
            socketChannel.close();
        } catch (IOException e) {
            String msg = String.format("Error disconnecting from STOMP connection: %s:%d", server, port);
            throw new StompException(msg, e);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }
}
