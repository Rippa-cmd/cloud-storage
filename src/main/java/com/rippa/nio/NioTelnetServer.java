package com.rippa.nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class NioTelnetServer {
    public static final String LS_COMMAND = "\tls    view all files and directories\n\r";
    public static final String MKDIR_COMMAND = "\tmkdir [dirname]    create directory\n\r";
    public static final String CHANGE_NICKNAME = "\tnick    change nickname\n\r";
    public static final String EXIT_COMMAND = "\texit    close connection\n\r";
    public static final String TOUCH_COMMAND = "\ttouch [filename]    create new file\n\r";
    public static final String CD_COMMAND = "\tcd [path]    moving through a folder\n\r";
    public static final String RM_COMMAND = "\trm [file | directory]    delete file or directory\n\r";
    public static final String COPY_COMMAND = "\tcopy [src] [target]    copy file or folder\n\r";


    public static String username = "User";
    public static Path curPath = Path.of("server");

    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    public NioTelnetServer() throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(5678));
        server.configureBlocking(false);
        // OP_ACCEPT, OP_READ, OP_WRITE
        Selector selector = Selector.open();

        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started");

        while (server.isOpen()) {
            selector.select();

            var selectionKeys = selector.selectedKeys();
            var iterator = selectionKeys.iterator();

            while (iterator.hasNext()) {
                var key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key, selector);
                }
                iterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        SocketAddress client = channel.getRemoteAddress();
        int readBytes = channel.read(buffer);
        if (readBytes < 0) {
            channel.close();
            return;
        } else if (readBytes == 0) {
            return;
        }

        buffer.flip();

        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }

        buffer.clear();

        if (key.isValid()) {
            String[] commands = sb
                    .toString()
                    .replace("\n", "")
                    .replace("\r", "").split(" ");

            if (commands.length == 1) {
                if ("--help".equals(commands[0])) {
                    sendMessage(LS_COMMAND, selector, client);
                    sendMessage(MKDIR_COMMAND, selector, client);
                    sendMessage(CHANGE_NICKNAME, selector, client);
                    sendMessage(TOUCH_COMMAND, selector, client);
                    sendMessage(RM_COMMAND, selector, client);
                    sendMessage(CD_COMMAND, selector, client);
                    sendMessage(COPY_COMMAND, selector, client);
                    sendMessage(EXIT_COMMAND, selector, client);
                } else if ("ls".equals(commands[0])) {
                    sendMessage(getFileList().concat("\n\r"), selector, client);
                } else if ("exit".equals(commands[0])) {
                    System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
                    channel.close();
                    curPath = Path.of("server");
                    return;
                }
            } else if (commands.length == 2) {
                if ("nick".equals(commands[0])) {
                    changeNick(commands[1]);
                } else if ("touch".equals(commands[0])) {
                    createFile(commands[1], selector, client);
                } else if ("rm".equals(commands[0])) {
                    newDel(toRootPath(commands[1]));
//                    deleteFileOrDirectory(Path.of(curPath.toString() + File.separator + commands[1]));
                } else if ("mkdir".equals(commands[0])) {
                    createFolder(commands[1]);
                } else if ("cd".equals(commands[0])) {
                    changeDirectory(commands[1], selector, client);
                } else if ("cat".equals(commands[0])) {
                    writeContent(commands[1], selector, client);
                }
            } else if (commands.length == 3) {
                if ("copy".equals(commands[0]))
                    copyFromTo(commands[1], commands[2]);
            }
            sendMessage(username + ":" + curPath + File.separator + "$ ", selector, client);
        }
    }

    /**
     * Copies file (files) of directory (directories) to target place
     *
     * @param source      source path
     * @param destination target path
     */
    private void copyFromTo(String source, String destination) {
        final Path src = toRootPath(source);
        final Path trg = toRootPath(destination + File.separator + src.getFileName());

        try {
            Files.walkFileTree(src, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path targetFile = trg.resolve(src.relativize(file));
                    Files.copy(file, targetFile);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path newDir = trg.resolve(src.relativize(dir));
                    Files.createDirectory(newDir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Converts raw path to path from root folder
     *
     * @param path path before processing
     * @return ready path
     */
    private Path toRootPath(String path) {
        if (path.contains("server"))
            return Path.of(path);
        else
            return curPath.resolve(path);
    }

    /**
     * Just changes nick
     *
     * @param newNick new nick
     */
    private void changeNick(String newNick) {
        username = newNick;
    }

    /**
     * Sends content of selected file if possible
     *
     * @param fileName path to file
     */
    private void writeContent(String fileName, Selector selector, SocketAddress client) throws IOException {
        Path newPath = curPath.resolve(fileName);
        if (Files.exists(newPath))
            if (!Files.isDirectory(newPath))
                try {
                    for (String s : Files.readAllLines(newPath))
                        sendMessage(s.concat("\n\r"), selector, client);
                } catch (Exception e) {
                    sendMessage("Can't read file\n\r", selector, client);
                }
    }

    /**
     * Changes current directory
     *
     * @param path path of new folder
     */
    private void changeDirectory(String path, Selector selector, SocketAddress client) throws IOException {
        if ("..".equals(path)) {
            if (!"server".equals(curPath.toString())) {
                curPath = curPath.getParent();
            } else
                sendMessage("You are already in root folder\n\r", selector, client);
            return;
        } else if ("~".equals(path)) {
            curPath = Path.of("server");
            return;
        }

        // вырезаем точку, если затесалась в конце названия папки, и если она одна
        if (path.charAt(path.length() - 1) == '.' && path.charAt(path.length() - 2) != '.')
            path = path.substring(0, path.length() - 1);

        Path newPath = curPath.resolve(path);
        if (Files.exists(newPath))
            curPath = newPath;
    }

    /**
     * Recursive deletion of attached files
     *
     * @param path path to file
     */
    private void deleteFileOrDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            String[] files = new File(path.toString()).list();
            if ((null != files) && (files.length != 0)) {
                for (String filename : files) {
                    deleteFileOrDirectory(path.resolve(filename));
                }
            }
        }
        Files.deleteIfExists(path);
    }

    /**
     * New recursive deletion of attached files (using FileTree)
     *
     * @param path path to file
     */
    private void newDel(Path path) {
        if ("server".equals(path.getFileName().toString()))
            return;
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (!Files.exists(curPath))
            curPath = curPath.getParent();

    }

    /**
     * Creates a new folder (folders)
     *
     * @param folderName - folder name
     */
    private void createFolder(String folderName) throws IOException {
        Files.createDirectories(curPath.resolve(folderName));
    }

    /**
     * Creates a new file
     *
     * @param fileName - file name
     */
    private void createFile(String fileName, Selector selector, SocketAddress client) throws IOException {
        Path newPath = curPath.resolve(fileName);
        ;
        if (!Files.exists(newPath)) {
            Files.createFile(newPath);
        } else
            sendMessage("File already exist\n\r", selector, client);
    }

    private String getFileList() {
        return String.join(" ", new File(String.valueOf(curPath)).list());
    }

    private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.channel() instanceof SocketChannel) {
                if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
                    ((SocketChannel) key.channel())
                            .write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        username = System.getProperty("user.name");
        System.out.println("Client accepted. IP: " + channel.getRemoteAddress());

        channel.register(selector, SelectionKey.OP_READ, "some attach");
        channel.write(ByteBuffer.wrap(("Hello, " + username + "!\n\r").getBytes(StandardCharsets.UTF_8)));
        channel.write(ByteBuffer.wrap("Enter --help for support info\n\r".getBytes(StandardCharsets.UTF_8)));
    }

    public static void main(String[] args) throws IOException {
        new NioTelnetServer();
    }
}