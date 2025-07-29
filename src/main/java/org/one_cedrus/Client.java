package org.one_cedrus;

import org.one_cedrus.manager.VaultManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.nio.file.Paths;

@Command(name = "sv", mixinStandardHelpOptions = true, version = "sv 1.0", description = "Share Vault - Folder synchronization tool")
public class Client implements Runnable {
    @Command(name = "monitor", description = "Create an vault or start to monitor an existing one")
    static class MonitorCommand implements Runnable {
        @Option(names = { "-v", "--vault" }, description = "Name of vault to monitor")
        private String vaultName;

        @Option(names = { "-f", "--folder" }, description = "Local folder to sync to", defaultValue = ".")
        private String folder;

        @Option(names = { "-d",
                "--debounce" }, description = "Debounce time before creating a change log (seconds)", defaultValue = "5")
        private String debounceSeconds;

        @Option(names = { "--server" }, description = "Server URL", defaultValue = "http://localhost:4289")
        private String serverUrl;

        @Override
        public void run() {
            Path linkedDirPath = Paths.get(folder).toAbsolutePath();

            if (vaultName == null) {
                if (!linkedDirPath.toFile().exists()) {
                    System.err.println("[ERROR]: Folder '" + linkedDirPath + "' does not exist");
                    System.exit(1);
                }

                if (!linkedDirPath.toFile().isDirectory()) {
                    System.err.println("[ERROR]: '" + linkedDirPath + "' is not a directory");
                    System.exit(1);
                }

                VaultManager vaultManager = new VaultManager(linkedDirPath, serverUrl);
                String maybeVaultName = vaultManager.getVaultName();

                try {
                    if (maybeVaultName != null) {
                        System.out.println("[INFO]: This folder is already linked with a vault, start monitoring!");

                        vaultManager.monitorVault(maybeVaultName, Integer.parseInt(debounceSeconds));
                        System.out.println("[INFO]: Connected to vault '" + maybeVaultName + "'");
                        System.out.println("[INFO]: Local folder: " + linkedDirPath);
                        System.out.println("[INFO]: Starting synchronization...");
                    } else {
                        System.out.println("[INFO]: Creating vault from folder: " + linkedDirPath);
                        String newVaultName = vaultManager.createVault(Integer.parseInt(debounceSeconds));

                        System.out.println("[INFO]: Vault created successfully!");
                        System.out.println("[INFO]: Vault name: " + newVaultName);
                        System.out.println("[INFO]: Share this name with others to give them access");
                        System.out.println("[INFO]: Starting monitoring...");
                    }

                    vaultManager.startMonitoring();
                } catch (Exception exception) {
                    System.err.println("[ERROR]: " + exception.getMessage());
                    System.exit(1);
                }
            } else {
                VaultManager vaultManager = new VaultManager(linkedDirPath, serverUrl);

                String maybeVaultName = vaultManager.getVaultName();
                if (maybeVaultName != null && maybeVaultName.equals(vaultName)) {
                    System.err.println("[ERROR]: This folder is already linked to vault '" +
                            maybeVaultName + "'");
                    System.exit(1);
                }

                System.out.println("[INFO]: Monitoring vault '" + vaultName + "' in folder: " + linkedDirPath);

                try {
                    vaultManager.monitorVault(vaultName, Integer.parseInt(debounceSeconds));

                    System.out.println("[INFO]: Connected to vault '" + vaultName + "'");
                    System.out.println("[INFO]: Local folder: " + linkedDirPath);
                    System.out.println("[INFO]: Starting synchronization...");

                    // Start monitoring
                    vaultManager.startMonitoring();
                } catch (Exception e) {
                    System.err.println("[ERROR]: " + e.getMessage());
                    System.exit(1);
                }
            }
        }
    }

    @Override
    public void run() {
        // If no subcommand is specified, show help
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new Client());
        cmd.addSubcommand("monitor", new MonitorCommand());

        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}
