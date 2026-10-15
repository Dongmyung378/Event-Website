import com.sun.tools.attach.VirtualMachine;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/** Gracefully closes the explicitly enabled admin bean of this verification JVM only. */
public class StopApplication {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].matches("[0-9]+")) {
            throw new IllegalArgumentException("Provide the verification application's process ID.");
        }
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        String address;
        try { address = vm.startLocalManagementAgent(); }
        finally { vm.detach(); }
        try (var connector = JMXConnectorFactory.connect(new JMXServiceURL(address))) {
            connector.getMBeanServerConnection().invoke(
                new ObjectName("org.springframework.boot:type=Admin,name=SpringApplication"),
                "shutdown", new Object[0], new String[0]);
        }
    }
}
