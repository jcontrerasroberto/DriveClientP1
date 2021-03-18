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
    private DataOutputStream dos;
    private DataInputStream dis;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    private Path retorno;

    public DriveClient() throws IOException, ClassNotFoundException {

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Iniciando el cliente");
        Socket socketcon = new Socket(dir, port);
        System.out.println("Conexion establecida con el servidor");
        dis = new DataInputStream(socketcon.getInputStream());
        dos = new DataOutputStream(socketcon.getOutputStream());
        ois = new ObjectInputStream(socketcon.getInputStream());
        oos = new ObjectOutputStream(socketcon.getOutputStream());
        while(true) {
            currentDir = Paths.get(dis.readUTF());
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
                dos.close();
                dis.close();
                ois.close();
                oos.close();
                socketcon.close();
                break;
            }else{
                System.out.println("Comando incorrecto");
                dos.writeUTF(action);
                dos.flush();
            }
        }
    }

    public void changeDir(String action) throws IOException {
        dos.writeUTF(action);
        dos.flush();
        String response = dis.readUTF();
        if(!response.equals("ok")){
            System.out.println(response);
        }
    }

    public void clearScreen(String action) throws IOException {
        dos.writeUTF(action);
        dos.flush();
    }

    public void createDir(String action) throws IOException {
        dos.writeUTF(action);
        dos.flush();
        String response = dis.readUTF();
        System.out.println(response);
    }

    public void backDir(String action) throws IOException {
        dos.writeUTF(action);
        dos.flush();
    }

    public void uploadFiles(String action) throws IOException {
        dos.writeUTF(action);
        dos.flush();
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        int returnVal = fileChooser.showOpenDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            dos.writeBoolean(true);
            dos.flush();
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
                dis.readUTF();
            }
        } else {
            System.out.println("Open command cancelled by user.");
            dos.writeBoolean(false);
            dos.flush();
        }
    }

    public void sendDir(File dir, Path p) throws IOException {
        createDir("mkdir "+dir.getName());
        changeDir("cd "+ dir.getName());
        for(File f: dir.listFiles()) {
            if(f.isDirectory()){
                sendDir(f, Paths.get(p.toString(), f.getName()));
            }else{
            }
        }
        backDir("cd ..");
    }

    public void listFiles(String action) throws IOException, ClassNotFoundException {
        dos.writeUTF(action);
        dos.flush();
        ArrayList<String> files = (ArrayList<String>) ois.readObject();
        for (String s : files) {
            System.out.println(s);
        }
    }

    public void exitProgram() throws IOException {
        dos.writeUTF("exit");
        dos.flush();
    }

    public void downloadFiles(String action) throws IOException, ClassNotFoundException {
        dos.writeUTF(action);
        dos.flush();
        String response = dis.readUTF();
        if (!response.equals("404")){
            JFileChooser ubicacionChooser = new JFileChooser();
            ubicacionChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int returnVal = ubicacionChooser.showOpenDialog(null);
            if (returnVal == JFileChooser.APPROVE_OPTION){
                dos.writeBoolean(true);
                dos.flush();
                Path downloadPath = Paths.get(ubicacionChooser.getSelectedFile().getAbsolutePath());
                if(response.equals("dirok")){
                    Archivo porDescargar = (Archivo) ois.readObject();
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
                    Archivo porDescargar = (Archivo) ois.readObject();
                    Path downloadFilePath = Paths.get(downloadPath.toString(), porDescargar.getNombre());
                    receiveFile(porDescargar, downloadFilePath.toString());
                }

            }else{
                dos.writeBoolean(false);
                dos.flush();
            }
        }
    }

    public void deleteFiles(String action) throws IOException {
        dos.writeUTF(action);
        dos.flush();
        String response = dis.readUTF();
        System.out.println(response);
    }

    public void sendFile(File fileDownload, String type) throws IOException {
        Archivo porEnviar = new Archivo();
        porEnviar.setNombre(fileDownload.getName());
        porEnviar.setSize(fileDownload.length());
        if(type.equals("DIR-ZIP")) porEnviar.setExt("DIR");
        else porEnviar.setExt(FilenameUtils.getExtension(fileDownload.getName()).toUpperCase(Locale.ROOT));
        DataInputStream fileInput = new DataInputStream(new FileInputStream(fileDownload.getAbsolutePath()));
        oos.writeObject(porEnviar);
        oos.flush();
        long enviado = 0;
        int parte=0, porcentaje=0;
        while (enviado<porEnviar.getSize()){
            byte[] bytes = new byte[1500];
            parte = fileInput.read(bytes);
            dos.write(bytes, 0, parte);
            dos.flush();
            enviado = enviado + parte;
            porcentaje = (int)((enviado*100)/porEnviar.getSize());
            System.out.println("\rEnviado el "+porcentaje+" % del archivo");
        }
    }

    public void receiveFile(Archivo porRecibir, String ruta) throws IOException, ClassNotFoundException {
        System.out.println("Recibiendo "+ porRecibir.getNombre() + " de tamanio "+ porRecibir.getSize());
        DataOutputStream fileout = new DataOutputStream(new FileOutputStream(ruta));
        long recibido = 0;
        int parte = 0, porcentaje = 0;
        while (recibido<porRecibir.getSize()){
            byte[] bytes = new byte[1500];
            parte = dis.read(bytes);
            fileout.write(bytes, 0, parte);
            fileout.flush();
            recibido = recibido+parte;
            porcentaje = (int)((recibido*100)/porRecibir.getSize());
            System.out.println("Recibido el "+ porcentaje +" % del archivo");
        }
        System.out.println("Deje de recibir");
        dos.writeUTF("Recibido");
        dos.flush();
        fileout.close();
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        DriveClient dc = new DriveClient();
    }
}
