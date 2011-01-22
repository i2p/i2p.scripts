/*
 * XPISignerView.java
 */

package xpisigner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;
import org.jdesktop.application.Action;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import org.jdesktop.application.TaskMonitor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import javax.swing.DefaultListModel;
import javax.swing.Timer;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.filechooser.FileFilter;
import net.i2p.crypto.TrustedUpdate;
import net.i2p.data.Base64;
import net.i2p.data.SigningPrivateKey;

/**
 * The application's main frame.
 */
public class XPISignerView extends FrameView {

    private final File top;
    private final File keys;
    private final DefaultListModel model;
    private File tempTop;
    private Manifest lastManifest = null;

    public XPISignerView(SingleFrameApplication app) {
        super(app);

        initComponents();

        // status bar initialization - message timeout, idle icon and busy animation, etc
        ResourceMap resourceMap = getResourceMap();
        int messageTimeout = resourceMap.getInteger("StatusBar.messageTimeout");
        messageTimer = new Timer(messageTimeout, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                statusMessageLabel.setText("");
            }
        });
        messageTimer.setRepeats(false);
        int busyAnimationRate = resourceMap.getInteger("StatusBar.busyAnimationRate");
        for (int i = 0; i < busyIcons.length; i++) {
            busyIcons[i] = resourceMap.getIcon("StatusBar.busyIcons[" + i + "]");
        }
        busyIconTimer = new Timer(busyAnimationRate, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                busyIconIndex = (busyIconIndex + 1) % busyIcons.length;
                statusAnimationLabel.setIcon(busyIcons[busyIconIndex]);
            }
        });
        idleIcon = resourceMap.getIcon("StatusBar.idleIcon");
        statusAnimationLabel.setIcon(idleIcon);
        progressBar.setVisible(false);

        // connecting action tasks to status bar via TaskMonitor
        TaskMonitor taskMonitor = new TaskMonitor(getApplication().getContext());
        taskMonitor.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();
                if ("started".equals(propertyName)) {
                    if (!busyIconTimer.isRunning()) {
                        statusAnimationLabel.setIcon(busyIcons[0]);
                        busyIconIndex = 0;
                        busyIconTimer.start();
                    }
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(true);
                } else if ("done".equals(propertyName)) {
                    busyIconTimer.stop();
                    statusAnimationLabel.setIcon(idleIcon);
                    progressBar.setVisible(false);
                    progressBar.setValue(0);
                } else if ("message".equals(propertyName)) {
                    String text = (String)(evt.getNewValue());
                    statusMessageLabel.setText((text == null) ? "" : text);
                    messageTimer.restart();
                } else if ("progress".equals(propertyName)) {
                    int value = (Integer)(evt.getNewValue());
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(value);
                }
            }
        });

        createChooser.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File file) {
                return file.isDirectory() || file.getName().endsWith(".xpi2p");
            }

            @Override
            public String getDescription() {
                return "I2P Plugin Thingies";
            }
        });

        top = new File(System.getenv("HOME"),".i2p-plugin-keys");
        tempTop = new File(top,"temp");
        keys = new File(top,"keys");
        keys.mkdirs();
        tempTop.mkdirs();

        model = new DefaultListModel();

        keyList.setModel(model);

        reloadList();
    }

    private void reloadList() {
        model.clear();
        for(String name : keys.list()) {
            model.addElement(name);
        }
    }

    @Action
    public void showAboutBox() {
        if (aboutBox == null) {
            JFrame mainFrame = XPISignerApp.getApplication().getMainFrame();
            aboutBox = new XPISignerAboutBox(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        XPISignerApp.getApplication().show(aboutBox);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        keyList = new javax.swing.JList();
        statusPanel = new javax.swing.JPanel();
        javax.swing.JSeparator statusPanelSeparator = new javax.swing.JSeparator();
        statusMessageLabel = new javax.swing.JLabel();
        statusAnimationLabel = new javax.swing.JLabel();
        progressBar = new javax.swing.JProgressBar();
        createButton = new javax.swing.JButton();
        signButton = new javax.swing.JButton();
        createChooser = new javax.swing.JFileChooser();
        pickName = new javax.swing.JDialog();
        createName = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        createCommit = new javax.swing.JButton();

        mainPanel.setName("mainPanel"); // NOI18N

        jScrollPane1.setName("jScrollPane1"); // NOI18N

        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(xpisigner.XPISignerApp.class).getContext().getResourceMap(XPISignerView.class);
        keyList.setToolTipText(resourceMap.getString("keyList.toolTipText")); // NOI18N
        keyList.setName("keyList"); // NOI18N
        jScrollPane1.setViewportView(keyList);

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 412, Short.MAX_VALUE)
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 265, Short.MAX_VALUE)
        );

        statusPanel.setName("statusPanel"); // NOI18N

        statusPanelSeparator.setName("statusPanelSeparator"); // NOI18N

        statusMessageLabel.setName("statusMessageLabel"); // NOI18N

        statusAnimationLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        statusAnimationLabel.setName("statusAnimationLabel"); // NOI18N

        progressBar.setName("progressBar"); // NOI18N

        createButton.setText(resourceMap.getString("createButton.text")); // NOI18N
        createButton.setName("createButton"); // NOI18N
        createButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createButtonActionPerformed(evt);
            }
        });

        signButton.setText(resourceMap.getString("signButton.text")); // NOI18N
        signButton.setName("signButton"); // NOI18N
        signButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                signButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout statusPanelLayout = new javax.swing.GroupLayout(statusPanel);
        statusPanel.setLayout(statusPanelLayout);
        statusPanelLayout.setHorizontalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(statusPanelSeparator, javax.swing.GroupLayout.DEFAULT_SIZE, 412, Short.MAX_VALUE)
            .addGroup(statusPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(statusMessageLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 388, Short.MAX_VALUE)
                .addComponent(statusAnimationLabel)
                .addContainerGap())
            .addGroup(statusPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(createButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(signButton)
                .addGap(60, 60, 60)
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        statusPanelLayout.setVerticalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(statusPanelLayout.createSequentialGroup()
                .addComponent(statusPanelSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(statusPanelLayout.createSequentialGroup()
                        .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(statusMessageLabel)
                            .addComponent(statusAnimationLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(createButton)
                            .addComponent(signButton)))
                    .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        createChooser.setFileFilter(null);
        createChooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_AND_DIRECTORIES);
        createChooser.setName("createChooser"); // NOI18N

        pickName.setName("pickName"); // NOI18N

        createName.setText(resourceMap.getString("createName.text")); // NOI18N
        createName.setName("createName"); // NOI18N

        jLabel1.setText(resourceMap.getString("jLabel1.text")); // NOI18N
        jLabel1.setName("jLabel1"); // NOI18N

        createCommit.setText(resourceMap.getString("createCommit.text")); // NOI18N
        createCommit.setName("createCommit"); // NOI18N
        createCommit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createCommitActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pickNameLayout = new javax.swing.GroupLayout(pickName.getContentPane());
        pickName.getContentPane().setLayout(pickNameLayout);
        pickNameLayout.setHorizontalGroup(
            pickNameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pickNameLayout.createSequentialGroup()
                .addContainerGap(55, Short.MAX_VALUE)
                .addComponent(createName, javax.swing.GroupLayout.PREFERRED_SIZE, 333, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pickNameLayout.createSequentialGroup()
                .addContainerGap(280, Short.MAX_VALUE)
                .addComponent(createCommit)
                .addGap(28, 28, 28))
            .addGroup(pickNameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addContainerGap(65, Short.MAX_VALUE))
        );
        pickNameLayout.setVerticalGroup(
            pickNameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pickNameLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(createName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(createCommit)
                .addContainerGap(32, Short.MAX_VALUE))
        );

        setComponent(mainPanel);
        setStatusBar(statusPanel);
    }// </editor-fold>//GEN-END:initComponents

    private void createButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createButtonActionPerformed
        pickName.setVisible(true);
        pickName.toFront();
    }//GEN-LAST:event_createButtonActionPerformed

    static final TimeZone utc = TimeZone.getTimeZone("UTC");
    static final DateFormat formatter = DateFormat.getDateInstance();
    static {
        formatter.setTimeZone(utc);
    }
    
    Properties config = new Properties();
    String pubKey = null;

    private void snarfKey(File src) throws FileNotFoundException, IOException {
        if(pubKey!=null) return;
        byte[] buf = new byte[0x1000];
        FileInputStream in = new FileInputStream(src);
        try {
            int amount = in.read(buf);
            pubKey = Base64.encode(buf,0,amount);
        } finally {
           in.close();
        }

        System.out.println("Signing with key "+pubKey);
    }

    private void signButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_signButtonActionPerformed
        createChooser.showOpenDialog(keyList);
        try {
            File src = createChooser.getSelectedFile();
            File key = new File(keys, (String) model.get(keyList.getSelectedIndex()));
            File pub = new File(key, "public");
            File priv = new File(key, "private");

            snarfKey(pub);

            File temp = File.createTempFile("xpisigner", ".jar", tempTop);
            boolean tempsrc = false;
            try {
                if(src.isDirectory()) {
                    File temp2 = File.createTempFile("copy", ".jar", tempTop);
                    try {
                        zipUp(src,temp2);
                    } finally {
                        src = temp2;
                        tempsrc = true;
                    }
                }

                zipCopy(src,temp);
                // config should be set now.

                File xpi = new File(top,config.getProperty("name","unnamed") + "-" +
                            config.getProperty("version") + ".xpi2p");

                TrustedUpdate.main(new String[]{"sign", temp.getPath(), xpi.getPath(),
                            priv.toString(), (String) config.get("version")});
            } finally {
                /*
                temp.delete();
                if(tempsrc)
                    src.delete();
                 * 
                 */
            }
        } catch (ZipException ex) {
            Logger.getLogger(XPISignerView.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(XPISignerView.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_signButtonActionPerformed

    private void createCommitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createCommitActionPerformed
        String name = createName.getText().replaceFirst("^ *", "").replaceFirst(" *$","");
        File dest = new File(keys,name);
        dest.mkdir();
        File pub = new File(dest,"public");
        File priv = new File(dest,"private");
        if(priv.exists()) {
            throw new RuntimeException("Key already exists!");
        }

        TrustedUpdate.main(new String[] {"keygen",pub.toString(),priv.toString()});

        priv.setReadable(true, true);
        priv.setWritable(false, false);
        priv.setExecutable(false, false);

        try {
            new File("logs").delete();
        } catch (Exception e) {}

        pub.setReadable(true,false);
        pub.setWritable(false, false);
        pub.setExecutable(false, false);

        reloadList();
        pickName.setVisible(false);
    }//GEN-LAST:event_createCommitActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton createButton;
    private javax.swing.JFileChooser createChooser;
    private javax.swing.JButton createCommit;
    private javax.swing.JTextField createName;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JList keyList;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JDialog pickName;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JButton signButton;
    private javax.swing.JLabel statusAnimationLabel;
    private javax.swing.JLabel statusMessageLabel;
    private javax.swing.JPanel statusPanel;
    // End of variables declaration//GEN-END:variables

    private final Timer messageTimer;
    private final Timer busyIconTimer;
    private final Icon idleIcon;
    private final Icon[] busyIcons = new Icon[15];
    private int busyIconIndex = 0;
    private JDialog aboutBox;

    private void zipUp(File src, File temp) throws FileNotFoundException, IOException {
        JarOutputStream zo;
        if(lastManifest==null) {
            zo = new JarOutputStream(new FileOutputStream(temp));
        } else {
            zo = new JarOutputStream(new FileOutputStream(temp),lastManifest);
        }
        try {
            for (File f : new FileWalker(src)) {
                String name = src.toURI().relativize(f.toURI()).getPath();
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(f.lastModified());                
                zo.putNextEntry(entry);

                if(!f.isFile()) continue;

                System.out.println("Walking "+f);
                FileInputStream in = new FileInputStream(f);
                try {
                    int len;
                    byte[] buf = new byte[0x400];
                    while ((len = in.read(buf)) > 0) {
                        zo.write(buf, 0, len);
                    }                    
                } finally {
                    in.close();
                }
            }
        } finally {
            zo.close();
        }

    }

    private void handleConfigFile(InputStream in, OutputStream out) throws IOException {
        config.load(in);

        if(null==config.getProperty("version")) {
                config.setProperty("version","0.1");
        }

        config.remove("date");
        config.setProperty("date",Long.toString(new Date().getTime()));

        config.remove("key");
        config.setProperty("key",pubKey);

        config.store(out, "XPISigner has messed with this file");
    }

    private void zipCopy(File src, File temp) throws FileNotFoundException, IOException {
        FileInputStream in = new FileInputStream(src);
        try {
            if(src.getName().endsWith(".xpi2p")) {
                in.skip(56); // how to calculate this...
            }
            JarInputStream zi = new JarInputStream(in);
            Manifest manifest = zi.getManifest();
            FileOutputStream out = new FileOutputStream(temp);
            try {
                JarOutputStream zo;
                if (manifest == null) {
                    zo = new JarOutputStream(out);
                } else {
                    zo = new JarOutputStream(out, manifest);
                }
                for (;;) {
                    JarEntry entry = zi.getNextJarEntry();
                    if(entry==null) break;
                    entry = (JarEntry)entry.clone();
                    entry.setCompressedSize(-1);                    
                    zo.putNextEntry(entry);

                    if(entry.getName().equals("plugin.config")) {
                        handleConfigFile(zi,zo);
                    } else {
                        int len;
                        byte[] buf = new byte[0x400];
                        while ((len = zi.read(buf)) > 0) {
                            zo.write(buf, 0, len);
                        }
                    }
                }
                zo.close();
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }

    }

}
