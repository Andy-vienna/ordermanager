package org.andy.ordermanager;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.UIManager;

public class FolderOnlyTreeCellRenderer extends javax.swing.tree.DefaultTreeCellRenderer {
    
	private static final long serialVersionUID = 1L;
	private final Icon folderIcon;

    public FolderOnlyTreeCellRenderer() {
        // Du kannst hier dein eigenes Icon laden:
        // folderIcon = new ImageIcon(getClass().getResource("/folder.png"));
        
        // Oder das Standard-Icon von Java verwenden
        folderIcon = UIManager.getIcon("FileView.directoryIcon");
    }

    @Override
    public Component getTreeCellRendererComponent(
            javax.swing.JTree tree,
            Object value,
            boolean selected,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

        // Nur wenn der Knoten KEIN Blatt ist (also Ordner), Icon setzen
        if (!leaf) {
            setIcon(folderIcon);
        } else {
            setIcon(null); // Keine Datei-Icons
        }

        return this;
    }
}