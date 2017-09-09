package me.albrt.serialport;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


/**
 * Project: serialport
 * Author: Albert Lloveras (@albert_lloveras)
 * Date: 30/06/2017.
 * Description: SerialPort main class responsible for identifying the operating system type and the processor
 * architecture to decide, if compatible, which native driver should be loaded. It also acts as a bridge between
 * the C/C++ native code and the portable cross-platform JAVA code of the library.
 */

public final class SerialPort {

    private static final String NATIVE_BASE_PATH = "drivers";

    //region Internal Enumerations
    /** Enumeration that describes the processor architectures supported by the library. */
    private enum Architecture{
        i686,
        AMD64
    }

    /** Enumeration that describes the operating systems supported by the library. */
    private enum OperatingSystem{
        Windows,
        OSX,
        Linux
    }
    //endregion

    //region Operating Systems Folders & Dynamic Library Extensions
    private static final Map<OperatingSystem,String> OS_FOLDERS = Collections.unmodifiableMap(
            new HashMap<OperatingSystem, String>() {{
                put(OperatingSystem.Windows, "windows");
                put(OperatingSystem.Linux, "linux");
                put(OperatingSystem.OSX, "osx");
            }}

    );

    private static final Map<OperatingSystem, String> OS_EXTENSIONS = Collections.unmodifiableMap(
            new HashMap<OperatingSystem, String>() {{
                put(OperatingSystem.Windows, "so");
                put(OperatingSystem.OSX, "dylib");
                put(OperatingSystem.Windows, "dll");
            }}
    );
    //endregion

    //region Architecture Folders
    private static final Map<Architecture,String> ARCH_FOLDERS = Collections.unmodifiableMap(
            new HashMap<Architecture, String>() {{
                put(Architecture.AMD64, "amd64");
                put(Architecture.i686, "i686");
            }}
    );
    //endregion

    // This class private property is used by native code to check if the current instance of the SerialPort class
    // has a serial port currently opened.
    private boolean isConnected = false;

    // Static constructor that is called the first time the SerialPort class is instantiated. This code ensures that
    // the matching native drivers are loaded and available for being used by the library. If the target machine is
    // not compatible with the library this method will raise an exception preventing any sub-sequent call to the
    // SerialPort object.
    static{
        try{
            OperatingSystem os = SerialPort.detectOperatingSystem();
            Architecture arch = SerialPort.detectArchitecture(os);
            SerialPort.loadNative(os,arch);
        }catch(Exception e){
            throw new ExceptionInInitializerError(e);
        }
    }

    /***
     * Attempts to detect the operating system installed on the target machine.
     * @return If the detection attempt succeeds it will return a value from OperatingSystem enumeration matching the
     * operating system running on the target machine.
     * @throws Exception An exception will be raised if target's machine operating system could not be identified or
     * unexpected error occurs during the identification attempt. The raised exception includes extended information
     * about the problem in the message field.
     */
    private static OperatingSystem detectOperatingSystem() throws Exception{

        // Get from JVM a string representing the operating system name.
        String osName = System.getProperty("os.name").toLowerCase();

        if(osName.contains("nux")) return OperatingSystem.Linux;
        if(osName.contains("mac")) return OperatingSystem.OSX;
        if(osName.contains("win")) return OperatingSystem.Windows;

        // If there is no match with one of the available operating systems
        // raise an exception notifying the user about the error.
        throw new Exception("Error: The operating system is not supported.");

    }

    /**
     * Attempts to detect the processor architecture of the target machine.
     * @param os The operating system of the target machine where processor's architecture has to be detected.
     * @return If the detection attempt succeeds it will return a value from Architectures enumeration matching the
     * processor architecture of the target machine.
     * @throws Exception An exception will be raised if target's machine architecture could not be identified or any
     * unexpected error occurs during the identification attempt. The raised exception includes extended information
     * about the problem in the message field.
     */
    private static Architecture detectArchitecture(OperatingSystem os) throws Exception{

        // Use the proper strategy to detect the hardware architecture depending the
        // operating system installed on the computer.
        if(os == OperatingSystem.Windows) return SerialPort.detectArchitectureWindows();
        if(os == OperatingSystem.OSX) return SerialPort.detectArchitectureOSX();
        if(os == OperatingSystem.Linux) return SerialPort.detectArchitectureLinux();

        // If any of the supported operating systems match raise an exception
        // notifying the user about the error.
        throw new Exception("Error: The operating system is not supported.");

    }

    /**
     * Attempts to detect the processor's architecture on a Windows platform.
     * @return If the detection attempt succeeds it will return a value from Architecture enumeration matching
     * target's machine processor architecture.
     * @throws Exception An exception will be raised if target machine's architecture could not be identified or any
     * unexpected error occurs during the identification attempt. The raised exception includes extended information
     * about the problem in the message field.
     */
    private static Architecture detectArchitectureWindows() throws Exception{

        // The following architecture detection strategy was found on:
        // https://blogs.msdn.microsoft.com/david.wang/2006/03/27/howto-detect-process-bitness/
        // The main strategy is the following:
        //   1) Check the environment variable %PROCESSOR_ARCHITEW6432%
        //   2) If defined, then architecture = %PROCESSOR_ARCHITEW6432%
        //   3) If not, then architecture = %PROCESSOR_ARCHITECTURE%

        String[] variables = new String[]{
            "%PROCESSOR_ARCHITEW6432%",
            "%PROCESSOR_ARCHITECTURE%"
        };

        String result = null;
        for (String variable : variables) {

            // Check the environment variable %PROCESSOR_ARCHITEW6432%" using CMD echo
            // command.
            Process process = Runtime.getRuntime().exec("cmd echo " + variable);
            process.waitFor();

            // Prepare a buffered reader to read command output.
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();
            reader.close();

            if (output == null) {
                // Notify the user that hardware architecture couldn't be identified.
                throw new Exception("Error: Unexpected error retrieving the hardware architecture.");
            } else if (!output.equals(variable)) {
                result = output;
                break;
            }
        }

        // Check that any of the commands returned a valid value. If not, raise an exception and
        // notify the user with a message. Otherwise, convert the result string to lower case and
        // check if it matches with any of the supported architectures.
        if(result == null) throw new Exception("Error: Couldn't identify hardware architecture.");
        else result = result.toLowerCase();

        switch(result){
            case "amd64": return Architecture.AMD64;
            case "x86": return Architecture.i686;
            default: throw new Exception("Error: The hardware architecture is not supported.");
        }

    }

    /**
     * Attempts to detect the processor's architecture on a OSX platform.
     * @return If the detection attempt succeeds it will return a value from Architecture enumeration matching
     * target's machine processor architecture.
     * @throws Exception An exception will be raised if target machine's architecture could not be identified or any
     * unexpected error occurs during the identification attempt. The raised exception includes extended information
     * about the problem in the message field.
     */
    private static Architecture detectArchitectureOSX() throws  Exception{
        // OSX was built on top of FreeBSD kernel. For that reason, we can use the same
        // strategy in both systems to retrieve the processor architecture.
        return SerialPort.detectArchitectureLinux();
    }

    /**
     * Attempts to detect the processor's architecture on a Linux platform.
     * @return If the detection attempt succeeds it will return a value from Architecture enumeration matching
     * target's machine processor architecture.
     * @throws Exception An exception will be raised if target machine's architecture could not be identified or any
     * unexpected error occurs during the identification attempt. The raised exception includes extended information
     * about the problem in the message field.
     */
    private static Architecture detectArchitectureLinux() throws  Exception{

        // Execute the uname -m command in bash terminal to get the processor architecture.
        Process process = Runtime.getRuntime().exec("uname -m");
        process.waitFor();

        // Prepare a buffered reader to read command output.
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String result = reader.readLine();
        reader.close();

        // If we couldn't read the hardware architecture raise an exception
        // notifying the user about the cause of the error. Otherwise, convert
        // the result string to lower case before doing the architecture name
        // check.
        if(result == null) throw new Exception("Error: Unexpected error retrieving the hardware architecture.");
        else result = result.toLowerCase();

        switch(result){
            case "i386": return Architecture.i686;
            case "i686": return Architecture.i686;
            case "amd64": return Architecture.AMD64;
            case "ia64": return Architecture.AMD64;
            case "x86_64": return Architecture.AMD64;
            default: throw new Exception("Error: The hardware architecture is not supported");
        }

    }


    /**
     * Given a valid operating system and architecture, loads the corresponding dynamic library that will receive the
     * native method calls. The dynamic library will act as a bridge to translate JAVA code calls into operating system
     * API calls and vice versa.
     * @param os The operating system of the target machine.
     * @param arch The processor architecture of the target machine.
     * @throws Exception An exception will be raised if the dynamic library loading attempt fails due to unexpected
     * reasons such as lack of permissions.
     */
    private static void loadNative(OperatingSystem os, Architecture arch) throws Exception{

        // Build up the path to the corresponding native driver dynamic library file.
        String sourcePath = NATIVE_BASE_PATH + File.separator + OS_FOLDERS.get(os) +
                File.separator + ARCH_FOLDERS.get(arch) + File.separator +  "serialport." + OS_EXTENSIONS.get(os);

        // Get the Java temporal folder.
        String systemTempPath = System.getProperty("java.io.tmpdir");

        // Create unique file name for the dynamic library file that
        // will be placed in system's temporal folder.
        String fileName = UUID.randomUUID().toString().replace("-","");

        // Build the unique destination file name.
        String destPath = systemTempPath +  fileName +  "." + OS_EXTENSIONS.get(os);

        // Get the stream of bytes of the native library that has to be loaded.
        InputStream sourceInputStream = (SerialPort.class.getResourceAsStream(sourcePath));

        // Create the destination file entity.
        File tempFile = new File(destPath);

        // Save the library byte stream into the temporal file.
        if(!Files.exists(tempFile.getCanonicalFile().toPath())){
            Files.copy(sourceInputStream,tempFile.getCanonicalFile().toPath());
        }

        if(!tempFile.setExecutable(true)){

            // Remove the temporal file and raise an exception notifying the user
            // about permission issues.
            tempFile.delete();
            throw new Exception(
                    "Error: Couldn't load the native driver code because the current user doesn't" +
                            "have enough privileges to execute code at (" + systemTempPath + ")"
            );

        }else{
            // Mark the temp file for automatic deletion when the current process ends and
            // load the dynamic library.
            tempFile.deleteOnExit();
            System.load(tempFile.getCanonicalFile().toString());
        }

    }


    /**
     * Gets the full list of available serial ports on the current machine.
     * @return Returns a list containing the name of all available serial ports on the current machine.
     */
    public native String [] getPortList();

    /**
     * Gets the full list of available baudrates supported by the operating system on the current machine.
     * @return Returns a list containing integer values that correspond to the different baudrates available on the
     * current machine.
     */
    public native int [] getAvailableBaudRates();

    /**
     * Configures the received port at the received baudrate and opens it to start a serial communication.
     * @param port The name of the port to configure and open. This name must exist on the list of available ports.
     * @param baudrate The desired baudrate for the selected port. This value must be present on the list of available
     *                 baudrates.
     * @throws Exception An exception will be raised when an unexpected error occurs during the process of configuring
     * or opening the port. Usually exceptions are raised due to a lack of permissions to use the selected ports or
     * becuase the port is already in use by another process in the system.
     */
    public native void openPort(String port,int baudrate) throws Exception;

    /**
     * Closes the port that has been previously opened by the same instance of this class. If any port has been already
     * opened, a call to this method won't do anything.
     */
    public native void closePort();

    /**
     * Performs a synchronous write on the transmission line of the serial port that has been previously opened by the
     * same instance of the SerialPort class.
     * @param b The byte that has to be written to the serial line.
     * @throws Exception An exception will be raised if the instance of the class doesn't have any port open or an
     * unexpected error occurs during the write operation.
     */
    public native void writeByte(byte b) throws Exception;

    /**
     * Performs a synchronous read on the reception line of the serial port that has been previously opened by the same
     * instance of the SerialPort class. This method will block the calling thread until a new byte is received or the
     * timeout period is elapsed.
     * @param timeout The amount of time (in milliseconds) that the call will block if no byte is available on the
     *                reception line of the serial port.
     * @return If a byte has been received the byte value will be returned. Otherwise, when timeout period is elapsed,
     * null value will be returned.
     * @throws Exception An exception will be raised if the current instance of the SerialPort class doesn't have any
     * port open or an unexpected error occurs while receiving a new byte.
     */
    public native byte readByte(int timeout) throws Exception;

    /**
     * Performs a synchronous read on the reception line of the serial port that has been previously opened by the same
     * instance of the SerialPort class. This method will block the calling thread until a new byte is received.
     * @return The received byte value.
     * @throws Exception An exception will be raised if the current instance of the SerialPort class doesn't have any
     * port open or an unexpected error occurs while receiving a new byte.
     */
    public native byte readByte() throws Exception;

}
