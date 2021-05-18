package com.rippa.server;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class ClientHandler implements Runnable {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())
        ) {
            while (true) {
                String command = in.readUTF();
                if ("upload".equals(command)) {
                    uploading(out, in);
                }
                if ("download".equals(command)) {
                    downloading(in, out);
                }
                if ("exit".equals(command)) {
                    out.writeUTF("DONE");
                    disconnected();
                    System.out.printf("Client %s disconnected correctly\n", socket.getInetAddress());
                    break;
                }
                System.out.println(command);
                //out.writeUTF(command); ИМХО лишнее, мешает корректной обработке статуса на клиентской стороне
            }
        } catch (SocketException socketException) {
            System.out.printf("Client %s disconnected\n", socket.getInetAddress());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * sending file to client side
     */
    private void downloading(DataInputStream in, DataOutputStream out) throws IOException {
        try {
            File file = new File("server/" + in.readUTF());
            if (!file.exists()) {
                throw new FileNotFoundException();
            } else out.writeUTF("File found");

            FileInputStream fis = new FileInputStream(file);
            long fileLength = file.length();

            out.writeLong(fileLength);

            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            fis.close();
        } catch (FileNotFoundException e) {
            out.writeUTF("FNF");
        }
    }

    private void uploading(DataOutputStream out, DataInputStream in) throws IOException {
        try {
            File file = new File("server/" + in.readUTF()); // read file name
            if (!file.exists()) {
                file.createNewFile();
            }

            FileOutputStream fos = new FileOutputStream(file);
            long size = in.readLong();
            byte[] buffer = new byte[8 * 1024];
            for (int i = 0; i < (size + (8 * 1024 - 1)) / (8 * 1024); i++) {
                int read = in.read(buffer);
                fos.write(buffer, 0, read);
            }
            fos.close();
            out.writeUTF("OK");
        } catch (Exception e) {
            out.writeUTF("WRONG");
        }
    }

    private void disconnected() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
