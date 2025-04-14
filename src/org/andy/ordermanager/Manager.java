package org.andy.ordermanager;

import javax.swing.*;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Manager extends JFrame {
	
	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = Logger.getLogger(Manager.class.getName());
	
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(listModel);
    private final JButton copyButton = new JButton("Start");
    private Path sourceDir;
    private Path targetDir;
    private Path archiveDir;
    
    private WatchService watchService;
    private boolean watching = false; // Flag, um zu überprüfen, ob wir bereits überwachen

	// ###################################################################################################################################################
	// ###################################################################################################################################################
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Manager::new);
    }
    
	// ###################################################################################################################################################
	// ###################################################################################################################################################
    
    public Manager() {
    	
    	super("AuftragsManager");

        loadConfig(); // <-- hier!
        
        try {
        	Files.createDirectories(sourceDir);
        	Files.createDirectories(targetDir);
			Files.createDirectories(archiveDir);
		} catch (IOException e) {
			logger.severe("fehler beim erzeugen der Pfade - " + e.getMessage());
		}
        
        setLayout(new BorderLayout());
        add(new JScrollPane(fileList), BorderLayout.CENTER);
        add(copyButton, BorderLayout.SOUTH);

        updateFileList();

        copyButton.addActionListener(_ -> moveSelectedFiles());

        setSize(400, 300);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setVisible(true);

        // Fenster auf der rechten Seite und volle Bildschirmhöhe
        positionWindow();
    }
    
    @Override
    public void dispose() {
        super.dispose();
        stopWatching(); // Sicherstellen, dass der WatchService beim Schließen gestoppt wird
    }
    
	// ###################################################################################################################################################
	// ###################################################################################################################################################

    private void positionWindow() {
        int fixedWidth = 500;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle screenBounds = ge.getMaximumWindowBounds(); // Arbeitsbereich ohne Taskleiste etc.

        int x = screenBounds.x + screenBounds.width - fixedWidth; // ganz rechts
        int y = screenBounds.y; // ganz oben
        int height = screenBounds.height;

        setBounds(x, y, fixedWidth, height);
    }

    private void loadConfig() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            props.load(fis);
            sourceDir = Paths.get(props.getProperty("sourceDir"));
            targetDir = Paths.get(props.getProperty("targetDir"));
            archiveDir = Paths.get(props.getProperty("archiveDir"));
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Fehler beim Laden der Konfiguration.", "Fehler", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void startWatching() {
        if (watching) return;  // Überprüfung, ob bereits überwacht wird

        watching = true; // Start der Überwachung
        Thread watcherThread = new Thread(() -> {
            try {
                watchService = FileSystems.getDefault().newWatchService();
                sourceDir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);

                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = null;
                    try {
                        key = watchService.take(); // blockiert bis ein Event kommt
                        if (key == null) continue; // Falls kein Key (durch Interruption)

                        for (WatchEvent<?> _ : key.pollEvents()) {
                            // Optional: System.out.println("Event: " + kind + " - " + event.context());
                        }

                        // Liste im Event-Dispatch-Thread aktualisieren
                        SwingUtilities.invokeLater(this::updateFileList);

                    } catch (ClosedWatchServiceException e) {
                        // Wir ignorieren diese Ausnahme, da sie beim ordnungsgemäßen Schließen des WatchService auftritt
                        break;
                    } catch (InterruptedException e) {
                        // Wenn der Thread unterbrochen wird, beenden wir die Schleife
                        break;
                    } finally {
                        if (key != null && !key.reset()) {
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                logger.severe("fehler beim Starten des WatchService - " + e.getMessage());
            }
        });

        watcherThread.setDaemon(true); // Beendet sich automatisch mit der App
        watcherThread.start();
    }
    
    private void stopWatching() {
        if (watchService != null) {
            try {
                watchService.close(); // Beendet den WatchService
            } catch (IOException e) {
                logger.severe("Fehler beim Schließen des WatchService - " + e.getMessage());
            }
        }
        watching = false; // Setze das Flag zurück
    }

    private void updateFileList() {
    	startWatching();
        try {
            listModel.clear();
            Map<String, List<Path>> grouped = Files.list(sourceDir)
                    .filter(Files::isRegularFile)
                    .collect(Collectors.groupingBy(path -> getBaseName(path.getFileName().toString())));

            for (String baseName : grouped.keySet()) {
                listModel.addElement(baseName);
            }

        } catch (IOException e) {
            logger.severe("Fehler beim Lesen des Quellverzeichnisses - " + e.getMessage());
        }
    }
    
    private void moveSelectedFiles() {
        String selectedBaseName = fileList.getSelectedValue();
        if (selectedBaseName == null) return;

        try {
        	// Alte Dateien ins Archiv verschieben
            moveFilesToArchive();
            
            // Zielordner bereinigen
            Files.walk(targetDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ex) {
                        logger.severe("Fehler beim Löschen der Datei - " + ex.getMessage());
                    }
                });

            // Alle passenden Dateien kopieren
            Files.list(sourceDir)
                .filter(Files::isRegularFile)
                .filter(path -> getBaseName(path.getFileName().toString()).equals(selectedBaseName))
                .forEach(path -> {
                    try {
                        Files.move(path, targetDir.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ex) {
                        logger.severe("Fehler beim Verschieben der Datei - " + ex.getMessage());
                    }
                });

            JOptionPane.showMessageDialog(this, "Dateien verschoben!");

        } catch (IOException e) {
            logger.severe("Fehler beim Verschieben der Dateien - " + e.getMessage());
        }
    }
    
    private void moveFilesToArchive() {
        
        try {
            Files.walk(targetDir)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            // Erstelle den neuen Archivpfad
                            Path archivePath = archiveDir.resolve(path.getFileName());
                            // Verschiebe die Datei ins Archiv
                            Files.move(path, archivePath, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException ex) {
                            logger.severe("Fehler beim Verschieben der Datei ins Archiv - " + ex.getMessage());
                        }
                    });
        } catch (IOException e) {
        	logger.severe("Fehler beim Verschieben der Datei ins Archiv - " + e.getMessage());
        }
    }

    private String getBaseName(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        String nameWithoutExtension = (dotIndex == -1) ? filename : filename.substring(0, dotIndex);
        
        // "_U" am Ende entfernen, egal ob Groß-/Kleinschreibung
        if (nameWithoutExtension.toLowerCase().endsWith("_u")) {
            nameWithoutExtension = nameWithoutExtension.substring(0, nameWithoutExtension.length() - 2);
        }

        return nameWithoutExtension;
    }

}
