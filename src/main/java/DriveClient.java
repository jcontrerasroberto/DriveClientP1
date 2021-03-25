import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Locale;

import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.zeroturnaround.zip.ZipUtil;

public class DriveClient {

    private final int port = 1234;
    private final String dir = "localhost";
    private Path currentDir;
    private Path rootDir;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    //private Socket socketcon;
    private Path retorno;

    public DriveClient() throws IOException, ClassNotFoundException {

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Iniciando el cliente");
        Socket socketcon = new Socket(dir, port);
        oos = new ObjectOutputStream(socketcon.getOutputStream());
        oos.flush();
        ois = new ObjectInputStream(socketcon.getInputStream());
        System.out.println("Conexion establecida con el servidor");

        while(true) {
            currentDir = Paths.get(receiveMessage());
            System.out.print("$" + currentDir + " > ");
            String action = br.readLine();
            if(action.equals("ls")) listFiles(action);
            else if(action.equals("cd ..")) backDir(action);
            else if(action.matches("cd \\w+")) changeDir(action);
            else if(action.matches("mkdir \\w+")) createDir(action);
            else if(action.equals("upload")) uploadFiles(action);
            else if(action.matches("download (\\w+\\.?(\\w+)?)")) downloadFiles(action);
            else if(action.matches("delete (\\w+\\.?(\\w+)?)")) deleteFiles(action);
            else if(action.equals("cls")) clearScreen(action);
            else if(action.equals("exit")) {
                exitProgram();
                oos.close();
                ois.close();
                socketcon.close();
                break;
            }else{
                System.out.println("Comando incorrecto");
                sendMessage(action);
            }
        }
    }

    public void changeDir(String action) throws IOException {
        sendMessage(action);
        String response = receiveMessage();
        if(!response.equals("ok")){
            System.out.println(response);
        }
    }

    public void clearScreen(String action) throws IOException {
        sendMessage(action);
    }

    public void createDir(String action) throws IOException {
        sendMessage(action);
        String response = receiveMessage();
        System.out.println(response);
    }

    public void backDir(String action) throws IOException {
        sendMessage(action);
    }

    public void uploadFiles(String action) throws IOException {
        sendMessage(action);
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setCurrentDirectory(new File("/home/robcb/PruebaDrive/"));
        int returnVal = fileChooser.showOpenDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            sendBoolean(true);
            File file = fileChooser.getSelectedFile();
            if(!file.isDirectory()){
                sendFile(file, "NODIR");
            }else{
                System.out.println("Mandando una carpeta");
                String zip_name = file.getAbsolutePath()+".zip";
                System.out.println(zip_name);
                ZipUtil.pack(file, new File(zip_name));
                sendFile(new File(zip_name), "DIR-ZIP");
                new File(zip_name).delete();
                //receiveMessage();
            }
        } else {
            System.out.println("Open command cancelled by user.");
            sendBoolean(false);
        }
    }

    public void listFiles(String action) throws IOException, ClassNotFoundException {
        sendMessage(action);
        ArrayList<String> files = (ArrayList<String>) receiveObject();
        for (String s : files) {
            System.out.println(s);
        }
    }

    public void exitProgram() throws IOException {
        sendMessage("exit");
    }

    public void downloadFiles(String action) throws IOException, ClassNotFoundException {
        sendMessage(action);
        String response = receiveMessage();
        if (!response.equals("404")){
            JFileChooser ubicacionChooser = new JFileChooser();
            ubicacionChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            ubicacionChooser.setCurrentDirectory(new File("/home/robcb/PruebaDrive/"));
            int returnVal = ubicacionChooser.showOpenDialog(null);
            if (returnVal == JFileChooser.APPROVE_OPTION){
                sendBoolean(true);
                Path downloadPath = Paths.get(ubicacionChooser.getSelectedFile().getAbsolutePath());
                if(response.equals("dirok")){
                    Archivo porDescargar = (Archivo) receiveObject();
                    System.out.println("Descargando directorio en " + downloadPath.toString());
                    System.out.println("Recibiendo una carpeta");
                    Path downloadFilePath = Paths.get(downloadPath.toString(), porDescargar.getNombre());
                    receiveFile(porDescargar, downloadFilePath.toString());
                    Path destino = Paths.get(downloadPath.toString(),FilenameUtils.removeExtension(porDescargar.getNombre()) );
                    System.out.println("Descomprimiendo " + downloadFilePath + " en " + destino.toString());
                    new ZipFile(downloadFilePath.toString()).extractAll(destino.toString());
                    File eliminar = new File(downloadFilePath.toString());
                    System.out.println("Eliminando "+ eliminar.getAbsolutePath());
                    eliminar.delete();
                }
                if(response.equals("fileok")){
                    Archivo porDescargar = (Archivo) receiveObject();
                    Path downloadFilePath = Paths.get(downloadPath.toString(), porDescargar.getNombre());
                    receiveFile(porDescargar, downloadFilePath.toString());
                }

            }else{
                sendBoolean(false);
            }
        }
    }

    public void deleteFiles(String action) throws IOException {
        sendMessage(action);
        String response = receiveMessage();
        System.out.println(response);
    }

    public void sendFile(File fileDownload, String type) throws IOException {
        Archivo porEnviar = new Archivo();
        porEnviar.setNombre(fileDownload.getName());
        porEnviar.setSize(fileDownload.length());
        if(type.equals("DIR-ZIP")) porEnviar.setExt("DIR");
        else porEnviar.setExt(FilenameUtils.getExtension(fileDownload.getName()).toUpperCase(Locale.ROOT));

        sendObject(porEnviar);

        DataInputStream fileInput = new DataInputStream(new FileInputStream(fileDownload.getAbsolutePath()));


        long tam = porEnviar.getSize();
        long enviados = 0;
        int l;

        while (enviados<tam){
            byte[] b = new byte[1500];
            l = fileInput.read(b);
            oos.write(b, 0, l);
            oos.flush();
            enviados += l;

        }

        fileInput.close();

        System.out.println("Archivo enviado");
    }

    public void receiveFile(Archivo porRecibir, String ruta) throws IOException, ClassNotFoundException {
        System.out.println("Recibiendo "+ porRecibir.getNombre() + " de tamanio "+ porRecibir.getSize());
        DataOutputStream fileout = new DataOutputStream(new FileOutputStream(ruta));

        long recibido = 0;
        int l;
        long tam = porRecibir.getSize();

        while(recibido<tam){
            byte[] b = new byte[1500];
            l = ois.read(b, 0, b.length);
            fileout.write(b, 0, l);
            fileout.flush();
            recibido += l;
        }

        fileout.close();
        System.out.println("Deje de recibir");
    }

    public void sendMessage(String mes){
        try {
            oos.writeUTF(mes);
            oos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String receiveMessage(){
        try {
            String res = ois.readUTF();
            return res;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void sendObject(Object toSend){
        try {
            oos.writeObject(toSend);
            oos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Object receiveObject(){
        try {
            Object rec = ois.readObject();
            return rec;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean receiveBoolean(){
        try {
            boolean res = ois.readBoolean();
            return res;
        } catch (IOException e) {

            e.printStackTrace();
            return false;
        }
    }

    public void sendBoolean(boolean bool){
        try {
            oos.writeBoolean(bool);
            oos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        DriveClient dc = new DriveClient();
    }
}
