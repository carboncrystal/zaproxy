/*
 *
 * Paros and its related class files.
 *
 * Paros is an HTTP/HTTPS proxy for assessing web application security.
 * Copyright (C) 2003-2004 Chinotec Technologies Company
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Clarified Artistic License
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Clarified Artistic License for more details.
 *
 * You should have received a copy of the Clarified Artistic License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
// ZAP: 2011/04/16 i18n
// ZAP: 2012/04/25 Added @Override annotation to the appropriate methods.
// ZAP: 2013/01/23 Clean up of exception handling/logging.
// ZAP: 2013/03/03 Issue 546: Remove all template Javadoc comments
// ZAP: 2013/12/03 Issue 933: Automatically determine install dir
// ZAP: 2014/03/23 Issue 412: Enable unsafe SSL/TLS renegotiation option not saved
// ZAP: 2014/08/14 Issue 1184: Improve support for IBM JDK
// ZAP: 2016/06/28: File chooser for PKCS#12 files now also accepts .pfx files
// ZAP: 2017/01/09 Remove method no longer needed.
// ZAP: 2017/01/23 Select first alias of selected keystore
// ZAP: 2017/08/16 Tidy up usage of CertificateView.
// ZAP: 2017/08/16 Show error message if failed to activate the certificate.
// ZAP: 2017/08/17 Reduce code duplication when showing cert/keystore errors
// ZAP: 2017/12/12 Use first alias by default (Issue 3879).
// ZAP: 2017/12/13 Do not allow to edit the name/key of active cert.
// ZAP: 2018/02/14 Remove unnecessary boxing / unboxing
// ZAP: 2018/03/29 Use FileNameExtensionFilter.
// ZAP: 2018/07/12 Fallback to bundled drivers.xml file.
// ZAP: 2018/09/19 GUI support for setting client certificate from CLI
// ZAP: 2019/06/01 Normalise line endings.
// ZAP: 2019/06/05 Normalise format/style.
// ZAP: 2020/11/26 Use Log4j 2 classes for logging.
// ZAP: 2022/05/21 Remove unsafe SSL/TLS renegotiation option.
package org.parosproxy.paros.extension.option;

// TODO: Buttons should be gray
import ch.csnc.extension.httpclient.PKCS11Configuration;
import ch.csnc.extension.httpclient.PKCS11Configuration.PCKS11ConfigurationBuilder;
import ch.csnc.extension.httpclient.SSLContextManager;
import ch.csnc.extension.ui.AliasTableModel;
import ch.csnc.extension.ui.CertificateView;
import ch.csnc.extension.ui.DriversView;
import ch.csnc.extension.util.DriverConfiguration;
import java.awt.CardLayout;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.ProviderException;
import java.security.cert.Certificate;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdesktop.swingx.JXHyperlink;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.model.OptionsParam;
import org.parosproxy.paros.view.AbstractParamPanel;
import org.zaproxy.zap.utils.ZapTextField;

public class OptionsCertificatePanel extends AbstractParamPanel {

    private static final long serialVersionUID = 4350957038174673492L;

    // Maximum number of login attempts per smartcard
    private static final int MAX_LOGIN_ATTEMPTS = 3;

    private javax.swing.JButton addPkcs11Button;
    private javax.swing.JButton addPkcs12Button;
    private javax.swing.JScrollPane aliasScrollPane;
    private javax.swing.JTable aliasTable;
    private javax.swing.JButton browseButton;
    private javax.swing.JLabel certificateLabel;
    private javax.swing.JPanel certificatePanel;
    private ZapTextField certificateTextField;
    private javax.swing.JTabbedPane certificatejTabbedPane;
    private javax.swing.JButton deleteButton;
    private javax.swing.JButton driverButton;
    private javax.swing.JComboBox<String> driverComboBox;
    private javax.swing.JLabel driverLabel;
    private javax.swing.JLabel fileLabel;
    private ZapTextField fileTextField;
    private javax.swing.JList<String> keyStoreList;
    private javax.swing.JPanel keyStorePanel;
    private javax.swing.JScrollPane keyStoreScrollPane;
    private javax.swing.JLabel passwordPkcs11Label;
    private javax.swing.JLabel passwordPkcs12Label;
    private javax.swing.JPanel pkcs11Panel;
    private javax.swing.JPasswordField pkcs11PasswordField;
    private javax.swing.JPanel pkcs12Panel;
    private javax.swing.JPasswordField pkcs12PasswordField;
    private javax.swing.JButton setActiveButton;
    private javax.swing.JButton showActiveCertificateButton;
    private javax.swing.JButton showAliasButton;
    private javax.swing.JLabel textLabel;
    private javax.swing.JCheckBox useClientCertificateCheckBox;
    private javax.swing.JCheckBox usePkcs11ExperimentalSliSupportCheckBox;

    private SSLContextManager contextManager;
    private DefaultListModel<String> keyStoreListModel;
    private AliasTableModel aliasTableModel;
    private DriverConfiguration driverConfig;

    // Issue 182
    private boolean retry = true;
    // Used if certificate is set from commandline
    private boolean overrideEnableClientCertificate = false;

    // Keep track of login attempts on PKCS11 smartcards to avoid blocking the smartcard
    private static int login_attempts = 0;

    private static final Logger logger = LogManager.getLogger(OptionsCertificatePanel.class);

    public OptionsCertificatePanel() {
        super();
        initialize();
    }

    /** This method initializes this */
    private void initialize() {

        contextManager =
                Model.getSingleton().getOptionsParam().getCertificateParam().getSSLContextManager();

        keyStoreListModel = new DefaultListModel<>();
        aliasTableModel = new AliasTableModel(contextManager);

        this.setLayout(new CardLayout());
        this.setName(Constant.messages.getString("options.cert.title"));

        JPanel certificatePanel = getPanelCertificate();
        this.add(certificatePanel, certificatePanel.getName());

        driverConfig = createDriverConfiguration();
        updateDriverComboBox();
        driverConfig.addChangeListener(e -> updateDriverComboBox());

        Certificate cert = contextManager.getDefaultCertificate();
        if (cert != null) {
            certificateTextField.setText(cert.toString());
        }

        if (contextManager.getKeyStoreCount() != 0) {
            overrideEnableClientCertificate = true;
        }
    }

    private static DriverConfiguration createDriverConfiguration() {
        String fileName = "drivers.xml";
        Path path = Paths.get(Constant.getZapInstall(), "xml", fileName);
        if (Files.exists(path)) {
            return new DriverConfiguration(path.toFile());
        }
        return new DriverConfiguration(
                OptionsCertificatePanel.class.getResource(
                        "/org/zaproxy/zap/resources/" + fileName));
    }

    private void updateDriverComboBox() {
        driverComboBox.removeAllItems();
        for (String name : driverConfig.getNames()) {
            driverComboBox.addItem(name);
        }
        driverComboBox.repaint();
    }
    /**
     * This method initializes panelCertificate
     *
     * @return javax.swing.JPanel
     */
    private JPanel getPanelCertificate() {
        if (certificatePanel == null) {

            // **************************************************************************
            // begin netbeans code
            // **************************************************************************
            certificatePanel = new javax.swing.JPanel();
            certificatejTabbedPane = new javax.swing.JTabbedPane();
            keyStorePanel = new javax.swing.JPanel();
            setActiveButton = new javax.swing.JButton();
            showAliasButton = new javax.swing.JButton();
            aliasScrollPane = new javax.swing.JScrollPane();
            aliasTable = new javax.swing.JTable();
            deleteButton = new javax.swing.JButton();
            keyStoreScrollPane = new javax.swing.JScrollPane();
            keyStoreList = new javax.swing.JList<>();
            pkcs12Panel = new javax.swing.JPanel();
            fileLabel = new javax.swing.JLabel();
            fileTextField = new ZapTextField();
            browseButton = new javax.swing.JButton();
            passwordPkcs12Label = new javax.swing.JLabel();
            addPkcs12Button = new javax.swing.JButton();
            pkcs12PasswordField = new javax.swing.JPasswordField();
            pkcs11Panel = new javax.swing.JPanel();
            driverLabel = new javax.swing.JLabel();
            driverComboBox = new javax.swing.JComboBox<>();
            driverButton = new javax.swing.JButton();
            passwordPkcs11Label = new javax.swing.JLabel();
            addPkcs11Button = new javax.swing.JButton();
            pkcs11PasswordField = new javax.swing.JPasswordField();
            useClientCertificateCheckBox = new javax.swing.JCheckBox();
            textLabel = new javax.swing.JLabel();
            certificateLabel = new javax.swing.JLabel();
            certificateTextField = new ZapTextField();
            showActiveCertificateButton = new javax.swing.JButton();
            usePkcs11ExperimentalSliSupportCheckBox = new javax.swing.JCheckBox();

            certificatejTabbedPane.setEnabled(false);

            setActiveButton.setText(Constant.messages.getString("options.cert.button.setactive"));
            setActiveButton.setEnabled(false);
            setActiveButton.addActionListener(
                    new java.awt.event.ActionListener() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                            try {
                                setActiveButtonActionPerformed(evt);
                            } catch (ProviderException e) {
                                showKeyStoreCertError(e.toString());
                                logger.error(e.getMessage(), e);
                            }
                        }
                    });

            showAliasButton.setText("->");
            showAliasButton.setEnabled(false);
            showAliasButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
            showAliasButton.addActionListener(
                    new java.awt.event.ActionListener() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                            showAliasButtonActionPerformed(evt);
                        }
                    });

            aliasTable.setModel(aliasTableModel);
            aliasTable.setTableHeader(null);
            aliasScrollPane.setViewportView(aliasTable);

            deleteButton.setText(Constant.messages.getString("options.cert.button.delete"));
            deleteButton.setEnabled(false);
            deleteButton.addActionListener(
                    new java.awt.event.ActionListener() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                            deleteButtonActionPerformed(evt);
                        }
                    });

            keyStoreList.setModel(keyStoreListModel);
            keyStoreList.addListSelectionListener(
                    new ListSelectionListener() {

                        @Override
                        public void valueChanged(ListSelectionEvent evt) {
                            keyStoreListSelectionChanged();
                        }
                    });
            keyStoreScrollPane.setViewportView(keyStoreList);

            javax.swing.GroupLayout keyStorePanelLayout =
                    new javax.swing.GroupLayout(keyStorePanel);
            keyStorePanel.setLayout(keyStorePanelLayout);
            keyStorePanelLayout.setHorizontalGroup(
                    keyStorePanelLayout
                            .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(
                                    javax.swing.GroupLayout.Alignment.TRAILING,
                                    keyStorePanelLayout
                                            .createSequentialGroup()
                                            .addGroup(
                                                    keyStorePanelLayout
                                                            .createParallelGroup(
                                                                    javax.swing.GroupLayout
                                                                            .Alignment.LEADING)
                                                            .addComponent(deleteButton)
                                                            .addComponent(
                                                                    keyStoreScrollPane,
                                                                    javax.swing.GroupLayout
                                                                            .DEFAULT_SIZE,
                                                                    181,
                                                                    Short.MAX_VALUE))
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addGroup(
                                                    keyStorePanelLayout
                                                            .createParallelGroup(
                                                                    javax.swing.GroupLayout
                                                                            .Alignment.LEADING)
                                                            .addGroup(
                                                                    keyStorePanelLayout
                                                                            .createSequentialGroup()
                                                                            .addComponent(
                                                                                    setActiveButton)
                                                                            .addPreferredGap(
                                                                                    javax.swing
                                                                                            .LayoutStyle
                                                                                            .ComponentPlacement
                                                                                            .RELATED,
                                                                                    100,
                                                                                    Short.MAX_VALUE)
                                                                            .addComponent(
                                                                                    showAliasButton))
                                                            .addComponent(
                                                                    aliasScrollPane,
                                                                    javax.swing.GroupLayout
                                                                            .DEFAULT_SIZE,
                                                                    202,
                                                                    Short.MAX_VALUE))));
            keyStorePanelLayout.setVerticalGroup(
                    keyStorePanelLayout
                            .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(
                                    javax.swing.GroupLayout.Alignment.TRAILING,
                                    keyStorePanelLayout
                                            .createSequentialGroup()
                                            .addGroup(
                                                    keyStorePanelLayout
                                                            .createParallelGroup(
                                                                    javax.swing.GroupLayout
                                                                            .Alignment.LEADING)
                                                            .addComponent(
                                                                    aliasScrollPane,
                                                                    0,
                                                                    0,
                                                                    Short.MAX_VALUE)
                                                            .addComponent(
                                                                    keyStoreScrollPane,
                                                                    javax.swing.GroupLayout
                                                                            .DEFAULT_SIZE,
                                                                    95,
                                                                    Short.MAX_VALUE))
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addGroup(
                                                    keyStorePanelLayout
                                                            .createParallelGroup(
                                                                    javax.swing.GroupLayout
                                                                            .Alignment.BASELINE)
                                                            .addComponent(deleteButton)
                                                            .addComponent(
                                                                    setActiveButton,
                                                                    javax.swing.GroupLayout
                                                                            .PREFERRED_SIZE,
                                                                    18,
                                                                    javax.swing.GroupLayout
                                                                            .PREFERRED_SIZE)
                                                            .addComponent(showAliasButton))));

            keyStorePanelLayout.linkSize(
                    javax.swing.SwingConstants.VERTICAL,
                    new java.awt.Component[] {deleteButton, setActiveButton, showAliasButton});

            certificatejTabbedPane.addTab(
                    Constant.messages.getString("options.cert.tab.keystore"), keyStorePanel);

            fileLabel.setText(Constant.messages.getString("options.cert.label.file"));

            browseButton.setText(Constant.messages.getString("options.cert.button.browse"));
            browseButton.addActionListener(
                    new java.awt.event.ActionListener() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                            browseButtonActionPerformed(evt);
                        }
                    });

            passwordPkcs12Label.setText(Constant.messages.getString("options.cert.label.password"));

            addPkcs12Button.setText(Constant.messages.getString("options.cert.button.keystore"));
            addPkcs12Button.addActionListener(
                    new java.awt.event.ActionListener() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                            addPkcs12ButtonActionPerformed(evt);
                        }
                    });

            javax.swing.GroupLayout pkcs12PanelLayout = new javax.swing.GroupLayout(pkcs12Panel);
            pkcs12Panel.setLayout(pkcs12PanelLayout);
            pkcs12PanelLayout.setHorizontalGroup(
                    pkcs12PanelLayout
                            .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(
                                    pkcs12PanelLayout
                                            .createSequentialGroup()
                                            .addGroup(
                                                    pkcs12PanelLayout
                                                            .createParallelGroup(
                                                                    javax.swing.GroupLayout
                                                                            .Alignment.LEADING)
                                                            .addGroup(
                                                                    javax.swing.GroupLayout
                                                                            .Alignment.TRAILING,
                                                                    pkcs12PanelLayout
                                                                            .createSequentialGroup()
                                                                            .addContainerGap()
                                                                            .addComponent(
                                                                                    fileTextField,
                                                                                    javax.swing
                                                                                            .GroupLayout
                                                                                            .DEFAULT_SIZE,
                                                                                    296,
                                                                                    Short.MAX_VALUE)
                                                                            .addPreferredGap(
                                                                                    javax.swing
                                                                                            .LayoutStyle
                                                                                            .ComponentPlacement
                                                                                            .RELATED)
                                                                            .addComponent(
                                                                                    browseButton))
                                                            .addGroup(
                                                                    pkcs12PanelLayout
                                                                            .createSequentialGroup()
                                                                            .addGap(12, 12, 12)
                                                                            .addComponent(
                                                                                    fileLabel))
                                                            .addGroup(
                                                                    pkcs12PanelLayout
                                                                            .createSequentialGroup()
                                                                            .addContainerGap()
                                                                            .addComponent(
                                                                                    passwordPkcs12Label))
                                                            .addGroup(
                                                                    javax.swing.GroupLayout
                                                                            .Alignment.TRAILING,
                                                                    pkcs12PanelLayout
                                                                            .createSequentialGroup()
                                                                            .addContainerGap(
                                                                                    270,
                                                                                    Short.MAX_VALUE)
                                                                            .addComponent(
                                                                                    addPkcs12Button))
                                                            .addGroup(
                                                                    pkcs12PanelLayout
                                                                            .createSequentialGroup()
                                                                            .addContainerGap()
                                                                            .addComponent(
                                                                                    pkcs12PasswordField,
                                                                                    javax.swing
                                                                                            .GroupLayout
                                                                                            .DEFAULT_SIZE,
                                                                                    369,
                                                                                    Short
                                                                                            .MAX_VALUE)))
                                            .addContainerGap()));
            pkcs12PanelLayout.setVerticalGroup(
                    pkcs12PanelLayout
                            .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(
                                    javax.swing.GroupLayout.Alignment.TRAILING,
                                    pkcs12PanelLayout
                                            .createSequentialGroup()
                                            .addComponent(fileLabel)
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addGroup(
                                                    pkcs12PanelLayout
                                                            .createParallelGroup(
                                                                    javax.swing.GroupLayout
                                                                            .Alignment.BASELINE)
                                                            .addComponent(browseButton)
                                                            .addComponent(
                                                                    fileTextField,
                                                                    javax.swing.GroupLayout
                                                                            .PREFERRED_SIZE,
                                                                    javax.swing.GroupLayout
                                                                            .DEFAULT_SIZE,
                                                                    javax.swing.GroupLayout
                                                                            .PREFERRED_SIZE))
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addComponent(passwordPkcs12Label)
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addComponent(
                                                    pkcs12PasswordField,
                                                    javax.swing.GroupLayout.PREFERRED_SIZE,
                                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                                    javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addComponent(addPkcs12Button)
                                            .addGap(70, 70, 70)));

            pkcs12PanelLayout.linkSize(
                    javax.swing.SwingConstants.VERTICAL,
                    new java.awt.Component[] {
                        addPkcs12Button, browseButton, fileTextField, pkcs12PasswordField
                    });

            certificatejTabbedPane.addTab(
                    Constant.messages.getString("options.cert.tab.pkcs"), pkcs12Panel);

            driverLabel.setText(Constant.messages.getString("options.cert.label.driver"));

            driverButton.setText("...");
            driverButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
            driverButton.addActionListener(
                    new java.awt.event.ActionListener() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                            driverButtonActionPerformed(evt);
                        }
                    });

            passwordPkcs11Label.setText(Constant.messages.getString("options.cert.label.pincode"));

            addPkcs11Button.setText(Constant.messages.getString("options.cert.button.pkcs11"));
            addPkcs11Button.addActionListener(
                    new java.awt.event.ActionListener() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                            addPkcs11ButtonActionPerformed(evt);
                        }
                    });

            usePkcs11ExperimentalSliSupportCheckBox.setText(
                    Constant.messages.getString(
                            "certificates.pkcs11.label.experimentalSliSupport"));
            usePkcs11ExperimentalSliSupportCheckBox.setBorder(
                    javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
            usePkcs11ExperimentalSliSupportCheckBox.setMargin(new java.awt.Insets(0, 0, 0, 0));
            usePkcs11ExperimentalSliSupportCheckBox.addActionListener(
                    new java.awt.event.ActionListener() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                            usePkcs11ExperimentalSliSupportCheckBoxActionPerformed(evt);
                        }
                    });

            javax.swing.GroupLayout pkcs11PanelLayout = new javax.swing.GroupLayout(pkcs11Panel);
            pkcs11Panel.setLayout(pkcs11PanelLayout);
            pkcs11PanelLayout.setHorizontalGroup(
                    pkcs11PanelLayout
                            .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(
                                    pkcs11PanelLayout
                                            .createSequentialGroup()
                                            .addContainerGap()
                                            .addGroup(
                                                    pkcs11PanelLayout
                                                            .createParallelGroup(
                                                                    javax.swing.GroupLayout
                                                                            .Alignment.LEADING)
                                                            .addComponent(
                                                                    pkcs11PasswordField,
                                                                    javax.swing.GroupLayout
                                                                            .DEFAULT_SIZE,
                                                                    369,
                                                                    Short.MAX_VALUE)
                                                            .addComponent(driverLabel)
                                                            .addComponent(passwordPkcs11Label)
                                                            .addGroup(
                                                                    pkcs11PanelLayout
                                                                            .createSequentialGroup()
                                                                            .addComponent(
                                                                                    driverComboBox,
                                                                                    0,
                                                                                    336,
                                                                                    Short.MAX_VALUE)
                                                                            .addPreferredGap(
                                                                                    javax.swing
                                                                                            .LayoutStyle
                                                                                            .ComponentPlacement
                                                                                            .RELATED)
                                                                            .addComponent(
                                                                                    driverButton))
                                                            .addComponent(
                                                                    usePkcs11ExperimentalSliSupportCheckBox)
                                                            .addComponent(
                                                                    addPkcs11Button,
                                                                    javax.swing.GroupLayout
                                                                            .Alignment.TRAILING))
                                            .addContainerGap()));
            pkcs11PanelLayout.setVerticalGroup(
                    pkcs11PanelLayout
                            .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(
                                    pkcs11PanelLayout
                                            .createSequentialGroup()
                                            .addComponent(driverLabel)
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addGroup(
                                                    pkcs11PanelLayout
                                                            .createParallelGroup(
                                                                    javax.swing.GroupLayout
                                                                            .Alignment.BASELINE)
                                                            .addComponent(driverButton)
                                                            .addComponent(
                                                                    driverComboBox,
                                                                    javax.swing.GroupLayout
                                                                            .PREFERRED_SIZE,
                                                                    17,
                                                                    javax.swing.GroupLayout
                                                                            .PREFERRED_SIZE))
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addComponent(passwordPkcs11Label)
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addComponent(
                                                    pkcs11PasswordField,
                                                    javax.swing.GroupLayout.PREFERRED_SIZE,
                                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                                    javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addComponent(usePkcs11ExperimentalSliSupportCheckBox)
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addComponent(addPkcs11Button)
                                            .addGap(58, 58, 58)));

            pkcs11PanelLayout.linkSize(
                    javax.swing.SwingConstants.VERTICAL,
                    new java.awt.Component[] {
                        addPkcs11Button, driverButton, driverComboBox, pkcs11PasswordField
                    });

            certificatejTabbedPane.addTab(
                    Constant.messages.getString("options.cert.tab.pkcs11"), pkcs11Panel);

            useClientCertificateCheckBox.setText(
                    Constant.messages.getString("options.cert.label.useclientcert"));
            useClientCertificateCheckBox.setBorder(
                    javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
            useClientCertificateCheckBox.setMargin(new java.awt.Insets(0, 0, 0, 0));
            useClientCertificateCheckBox.addActionListener(
                    new java.awt.event.ActionListener() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                            useClientCertificateCheckBoxActionPerformed(evt);
                        }
                    });

            textLabel.setText(Constant.messages.getString("options.cert.label.addkeystore"));

            certificateLabel.setText(Constant.messages.getString("options.cert.label.activecerts"));

            certificateTextField.setEnabled(false);
            certificateTextField.setEditable(false);

            showActiveCertificateButton.setText("->");
            showActiveCertificateButton.setActionCommand(">");
            showActiveCertificateButton.setEnabled(false);
            showActiveCertificateButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
            showActiveCertificateButton.addActionListener(
                    new java.awt.event.ActionListener() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                            showActiveCertificateButtonActionPerformed(evt);
                        }
                    });

            javax.swing.GroupLayout certificatePanelLayout =
                    new javax.swing.GroupLayout(certificatePanel);
            certificatePanel.setLayout(certificatePanelLayout);
            certificatePanelLayout.setHorizontalGroup(
                    certificatePanelLayout
                            .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(
                                    certificatePanelLayout
                                            .createParallelGroup(
                                                    javax.swing.GroupLayout.Alignment.LEADING)
                                            .addGroup(
                                                    certificatePanelLayout
                                                            .createSequentialGroup()
                                                            .addComponent(
                                                                    textLabel,
                                                                    0,
                                                                    0,
                                                                    Short.MAX_VALUE)
                                                            .addContainerGap())
                                            .addGroup(
                                                    certificatePanelLayout
                                                            .createSequentialGroup()
                                                            .addGap(2, 2, 2)
                                                            .addGroup(
                                                                    certificatePanelLayout
                                                                            .createParallelGroup(
                                                                                    javax.swing
                                                                                            .GroupLayout
                                                                                            .Alignment
                                                                                            .LEADING)
                                                                            .addComponent(
                                                                                    certificatejTabbedPane,
                                                                                    javax.swing
                                                                                            .GroupLayout
                                                                                            .DEFAULT_SIZE,
                                                                                    394,
                                                                                    Short.MAX_VALUE)
                                                                            .addGroup(
                                                                                    certificatePanelLayout
                                                                                            .createSequentialGroup()
                                                                                            .addGroup(
                                                                                                    certificatePanelLayout
                                                                                                            .createParallelGroup(
                                                                                                                    javax
                                                                                                                            .swing
                                                                                                                            .GroupLayout
                                                                                                                            .Alignment
                                                                                                                            .LEADING)
                                                                                                            .addComponent(
                                                                                                                    useClientCertificateCheckBox)
                                                                                                            .addComponent(
                                                                                                                    certificateLabel)
                                                                                                            .addGroup(
                                                                                                                    javax
                                                                                                                            .swing
                                                                                                                            .GroupLayout
                                                                                                                            .Alignment
                                                                                                                            .TRAILING,
                                                                                                                    certificatePanelLayout
                                                                                                                            .createSequentialGroup()
                                                                                                                            .addComponent(
                                                                                                                                    certificateTextField,
                                                                                                                                    javax
                                                                                                                                            .swing
                                                                                                                                            .GroupLayout
                                                                                                                                            .DEFAULT_SIZE,
                                                                                                                                    363,
                                                                                                                                    Short
                                                                                                                                            .MAX_VALUE)
                                                                                                                            .addPreferredGap(
                                                                                                                                    javax
                                                                                                                                            .swing
                                                                                                                                            .LayoutStyle
                                                                                                                                            .ComponentPlacement
                                                                                                                                            .UNRELATED)
                                                                                                                            .addComponent(
                                                                                                                                    showActiveCertificateButton)
                                                                                                                            .addPreferredGap(
                                                                                                                                    javax
                                                                                                                                            .swing
                                                                                                                                            .LayoutStyle
                                                                                                                                            .ComponentPlacement
                                                                                                                                            .RELATED)))
                                                                                            .addContainerGap())))));
            certificatePanelLayout.setVerticalGroup(
                    certificatePanelLayout
                            .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(
                                    certificatePanelLayout
                                            .createSequentialGroup()
                                            .addContainerGap()
                                            .addComponent(textLabel)
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .UNRELATED)
                                            .addComponent(useClientCertificateCheckBox)
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addComponent(
                                                    certificatejTabbedPane,
                                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                                    152,
                                                    Short.MAX_VALUE)
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addComponent(certificateLabel)
                                            .addPreferredGap(
                                                    javax.swing.LayoutStyle.ComponentPlacement
                                                            .RELATED)
                                            .addGroup(
                                                    certificatePanelLayout
                                                            .createParallelGroup(
                                                                    javax.swing.GroupLayout
                                                                            .Alignment.BASELINE)
                                                            .addComponent(
                                                                    certificateTextField,
                                                                    javax.swing.GroupLayout
                                                                            .PREFERRED_SIZE,
                                                                    javax.swing.GroupLayout
                                                                            .DEFAULT_SIZE,
                                                                    javax.swing.GroupLayout
                                                                            .PREFERRED_SIZE)
                                                            .addComponent(
                                                                    showActiveCertificateButton,
                                                                    javax.swing.GroupLayout
                                                                            .DEFAULT_SIZE,
                                                                    javax.swing.GroupLayout
                                                                            .DEFAULT_SIZE,
                                                                    Short.MAX_VALUE))
                                            .addContainerGap()));

            certificatePanelLayout.linkSize(
                    javax.swing.SwingConstants.VERTICAL,
                    new java.awt.Component[] {certificateTextField, showActiveCertificateButton});

            javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
            getContentPane().setLayout(layout);
            layout.setHorizontalGroup(
                    layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(
                                    certificatePanel,
                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                    Short.MAX_VALUE));
            layout.setVerticalGroup(
                    layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(
                                    layout.createSequentialGroup()
                                            .addComponent(
                                                    certificatePanel,
                                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                                    Short.MAX_VALUE)
                                            .addContainerGap()));

            // **************************************************************************
            // end netbeans code
            // **************************************************************************
        }
        return certificatePanel;
    }

    private static void showKeyStoreCertError(String errorMessage) {
        showCertError("options.cert.error.accesskeystore", errorMessage);
    }

    private static void showCertError(String i18nKeyBaseMessage, String errorMessage) {
        JOptionPane.showMessageDialog(
                null,
                new String[] {Constant.messages.getString(i18nKeyBaseMessage), errorMessage},
                Constant.messages.getString("options.cert.error"),
                JOptionPane.ERROR_MESSAGE);
    }

    private void keyStoreListSelectionChanged() {
        int keystore = keyStoreList.getSelectedIndex();
        try {
            aliasTableModel.setKeystore(keystore);
        } catch (Exception e) {
            showKeyStoreCertError(e.toString());
            logger.error(e.getMessage(), e);
        }
    }

    private void showActiveCertificateButtonActionPerformed(
            java.awt.event.ActionEvent
                    evt) { // GEN-FIRST:event_showActiveCertificateButtonActionPerformed
        Certificate cert = contextManager.getDefaultCertificate();
        if (cert != null) {
            showCertificate(cert);
        }
    } // GEN-LAST:event_showActiveCertificateButtonActionPerformed

    private void addPkcs11ButtonActionPerformed(
            java.awt.event.ActionEvent evt) { // GEN-FIRST:event_addPkcs11ButtonActionPerformed
        String name = null;
        try {
            final int indexSelectedDriver = driverComboBox.getSelectedIndex();
            name = driverConfig.getNames().get(indexSelectedDriver);
            if (name.equals("")) {
                return;
            }

            String library = driverConfig.getPaths().get(indexSelectedDriver);
            if (library.equals("")) {
                return;
            }

            int slot = driverConfig.getSlots().get(indexSelectedDriver);
            if (slot < 0) {
                return;
            }

            int slotListIndex = driverConfig.getSlotIndexes().get(indexSelectedDriver);
            if (slotListIndex < 0) {
                return;
            }

            String kspass = new String(pkcs11PasswordField.getPassword());
            if (kspass.equals("")) {
                kspass = null;
            }

            PCKS11ConfigurationBuilder confBuilder = PKCS11Configuration.builder();
            confBuilder.setName(name).setLibrary(library);
            if (usePkcs11ExperimentalSliSupportCheckBox.isSelected()) {
                confBuilder.setSlotListIndex(slotListIndex);
            } else {
                confBuilder.setSlotId(slot);
            }

            int ksIndex = contextManager.initPKCS11(confBuilder.build(), kspass);

            if (ksIndex == -1) {
                logger.error(
                        "The required PKCS#11 provider is not available ("
                                + SSLContextManager.SUN_PKCS11_CANONICAL_CLASS_NAME
                                + " or "
                                + SSLContextManager.IBM_PKCS11_CANONICAL_CLASS_NAME
                                + ").");
                showErrorMessageSunPkcs11ProviderNotAvailable();
                return;
            }

            // The PCKS11 driver/smartcard was initialized properly: reset login attempts
            login_attempts = 0;
            keyStoreListModel.insertElementAt(
                    contextManager.getKeyStoreDescription(ksIndex), ksIndex);
            // Issue 182
            retry = true;

            certificatejTabbedPane.setSelectedIndex(0);
            activateFirstOnlyAliasOfKeyStore(ksIndex);

            driverComboBox.setSelectedIndex(-1);
            pkcs11PasswordField.setText("");

        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof ProviderException) {
                if ("Error parsing configuration".equals(e.getCause().getMessage())) {
                    // There was a problem with the configuration provided:
                    //   - Missing library.
                    //   - Malformed configuration.
                    //   - ...
                    logAndShowGenericErrorMessagePkcs11CouldNotBeAdded(false, name, e);
                } else if ("Initialization failed".equals(e.getCause().getMessage())) {
                    // The initialisation may fail because of:
                    //   - no smart card reader or smart card detected.
                    //   - smart card is in use by other application.
                    //   - ...

                    // Issue 182: Try to instantiate the PKCS11 provider twice if there are
                    // conflicts with other software (e.g.. Firefox), that is accessing it too.
                    if (retry) {
                        // Try two times only
                        retry = false;
                        addPkcs11ButtonActionPerformed(evt);
                    } else {
                        JOptionPane.showMessageDialog(
                                null,
                                new String[] {
                                    Constant.messages.getString("options.cert.error"),
                                    Constant.messages.getString("options.cert.error.pkcs11")
                                },
                                Constant.messages.getString("options.cert.label.client.cert"),
                                JOptionPane.ERROR_MESSAGE);
                        // Error message changed to explain that user should try to add it again...
                        retry = true;
                        logger.warn("Couldn't add key from " + name, e);
                    }
                } else {
                    logAndShowGenericErrorMessagePkcs11CouldNotBeAdded(false, name, e);
                }
            } else {
                logAndShowGenericErrorMessagePkcs11CouldNotBeAdded(false, name, e);
            }
        } catch (java.io.IOException e) {
            if (e.getMessage().equals("load failed")
                    && e.getCause()
                            .getClass()
                            .getName()
                            .equals("javax.security.auth.login.FailedLoginException")) {
                // Exception due to a failed login attempt: BAD PIN or password
                login_attempts++;
                String attempts = " (" + login_attempts + "/" + MAX_LOGIN_ATTEMPTS + ") ";
                if (login_attempts == (MAX_LOGIN_ATTEMPTS - 1)) {
                    // Last attempt before blocking the smartcard
                    JOptionPane.showMessageDialog(
                            null,
                            new String[] {
                                Constant.messages.getString("options.cert.error"),
                                Constant.messages.getString("options.cert.error.wrongpassword"),
                                Constant.messages.getString("options.cert.error.wrongpasswordlast"),
                                attempts
                            },
                            Constant.messages.getString("options.cert.label.client.cert"),
                            JOptionPane.ERROR_MESSAGE);
                    logger.warn(
                            "PKCS#11: Incorrect PIN or password"
                                    + attempts
                                    + ": "
                                    + name
                                    + " *LAST TRY BEFORE BLOCKING*");
                } else {
                    JOptionPane.showMessageDialog(
                            null,
                            new String[] {
                                Constant.messages.getString("options.cert.error"),
                                Constant.messages.getString("options.cert.error.wrongpassword"),
                                attempts
                            },
                            Constant.messages.getString("options.cert.label.client.cert"),
                            JOptionPane.ERROR_MESSAGE);
                    logger.warn("PKCS#11: Incorrect PIN or password" + attempts + ": " + name);
                }
            } else {
                logAndShowGenericErrorMessagePkcs11CouldNotBeAdded(false, name, e);
            }
        } catch (KeyStoreException e) {
            logAndShowGenericErrorMessagePkcs11CouldNotBeAdded(false, name, e);
        } catch (Exception e) {
            logAndShowGenericErrorMessagePkcs11CouldNotBeAdded(true, name, e);
        }
    } // GEN-LAST:event_addPkcs11ButtonActionPerformed

    private void activateFirstOnlyAliasOfKeyStore(int ksIndex) {
        if (ksIndex < 0 || ksIndex >= keyStoreList.getModel().getSize()) {
            return;
        }

        keyStoreList.setSelectedIndex(ksIndex);
        if (aliasTable.getRowCount() != 0) {
            aliasTable.setRowSelectionInterval(0, 0);

            if (aliasTable.getRowCount() == 1 && !isCertActive()) {
                setActiveAction();
            }
        }
    }

    private boolean isCertActive() {
        String currentKey = contextManager.getDefaultKey();
        return currentKey != null && !currentKey.isEmpty();
    }

    private void showErrorMessageSunPkcs11ProviderNotAvailable() {
        final String sunReference =
                Constant.messages.getString("options.cert.error.pkcs11notavailable.sun.hyperlink");
        final String ibmReference =
                Constant.messages.getString("options.cert.error.pkcs11notavailable.ibm.hyperlink");
        Object[] hyperlinks = new Object[2];
        try {
            JXHyperlink hyperlinkLabel = new JXHyperlink();
            hyperlinkLabel.setURI(URI.create(sunReference));
            hyperlinkLabel.setText(
                    Constant.messages.getString(
                            "options.cert.error.pkcs11notavailable.sun.hyperlink.text"));
            hyperlinks[0] = hyperlinkLabel;

            hyperlinkLabel = new JXHyperlink();
            hyperlinkLabel.setURI(URI.create(ibmReference));
            hyperlinkLabel.setText(
                    Constant.messages.getString(
                            "options.cert.error.pkcs11notavailable.ibm.hyperlink.text"));
            hyperlinks[1] = hyperlinkLabel;
        } catch (UnsupportedOperationException e) {
            // Show plain text instead of a hyperlink if the current platform doesn't support
            // Desktop.
            hyperlinks[0] = sunReference;
            hyperlinks[1] = ibmReference;
        }

        JOptionPane.showMessageDialog(
                null,
                new Object[] {
                    Constant.messages.getString("options.cert.error"),
                    Constant.messages.getString("options.cert.error.pkcs11notavailable"),
                    hyperlinks
                },
                Constant.messages.getString("options.cert.label.client.cert"),
                JOptionPane.ERROR_MESSAGE);
    }

    private void logAndShowGenericErrorMessagePkcs11CouldNotBeAdded(
            boolean isErrorLevel, String name, Exception e) {
        if (pkcs11PasswordField.getPassword().length == 0) {
            JOptionPane.showMessageDialog(
                    null,
                    new String[] {
                        Constant.messages.getString("options.cert.error"),
                        Constant.messages.getString("options.cert.error.password.blank")
                    },
                    Constant.messages.getString("options.cert.label.client.cert"),
                    JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(
                    null,
                    new String[] {
                        Constant.messages.getString("options.cert.error"),
                        Constant.messages.getString("options.cert.error.password")
                    },
                    Constant.messages.getString("options.cert.label.client.cert"),
                    JOptionPane.ERROR_MESSAGE);
            if (isErrorLevel) {
                logger.error("Couldn't add key from " + name, e);
            } else {
                logger.warn("Couldn't add key from " + name, e);
            }
        }
    }

    private void driverButtonActionPerformed(
            java.awt.event.ActionEvent evt) { // GEN-FIRST:event_driverButtonActionPerformed
        new JDialog(new DriversView(driverConfig), true);
    } // GEN-LAST:event_driverButtonActionPerformed

    private void addPkcs12ButtonActionPerformed(
            java.awt.event.ActionEvent evt) { // GEN-FIRST:event_addPkcs12ButtonActionPerformed
        if (fileTextField.getText().equals("")) {
            return;
        }
        String kspass = new String(pkcs12PasswordField.getPassword());
        if (kspass.equals("")) {
            // pcks#12 file with empty password is not supported by java
            showCertError(
                    "options.cert.error.pkcs12nopass",
                    Constant.messages.getString("options.cert.error.usepassfile"));
            return;
        }

        int ksIndex;
        try {
            ksIndex = contextManager.loadPKCS12Certificate(fileTextField.getText(), kspass);
            keyStoreListModel.insertElementAt(
                    contextManager.getKeyStoreDescription(ksIndex), ksIndex);
        } catch (Exception e) {
            showKeyStoreCertError(Constant.messages.getString("options.cert.error.password"));
            logger.error(e.getMessage(), e);
            return;
        }

        certificatejTabbedPane.setSelectedIndex(0);
        activateFirstOnlyAliasOfKeyStore(ksIndex);

        fileTextField.setText("");
        pkcs12PasswordField.setText("");
    } // GEN-LAST:event_addPkcs12ButtonActionPerformed

    private void browseButtonActionPerformed(
            java.awt.event.ActionEvent evt) { // GEN-FIRST:event_browseButtonActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(
                new FileNameExtensionFilter(
                        Constant.messages.getString("options.cert.label.client.cert")
                                + " (*.p12, *.pfx)",
                        "p12",
                        "pfx"));

        int state = fc.showOpenDialog(null);

        if (state == JFileChooser.APPROVE_OPTION) {
            fileTextField.setText(fc.getSelectedFile().toString());
        }
    } // GEN-LAST:event_browseButtonActionPerformed

    private void showAliasButtonActionPerformed(
            java.awt.event.ActionEvent evt) { // GEN-FIRST:event_showAliasButtonActionPerformed
        int keystore = keyStoreList.getSelectedIndex();
        if (keystore >= 0) {
            int alias = aliasTable.getSelectedRow();
            Certificate cert = contextManager.getCertificate(keystore, alias);
            if (cert != null) {
                showCertificate(cert);
            }
        }
    } // GEN-LAST:event_showAliasButtonActionPerformed

    /**
     * Shows a second {@link JFrame} displaying the content of the certificate
     *
     * @param cert
     */
    @SuppressWarnings("unused")
    private void showCertificate(Certificate cert) {
        if (cert != null) {
            new CertificateView(cert.toString());
        }
    }

    private void setActiveButtonActionPerformed(
            java.awt.event.ActionEvent evt) { // GEN-FIRST:event_setActiveButtonActionPerformed
        setActiveAction();
    } // GEN-LAST:event_setActiveButtonActionPerformed

    private void setActiveAction() {
        int ks = keyStoreList.getSelectedIndex();
        int alias = aliasTable.getSelectedRow();
        if (ks > -1 && alias > -1) {
            if (!contextManager.isKeyUnlocked(ks, alias)) {

                try {
                    if (!contextManager.unlockKeyWithDefaultPassword(ks, alias)) {
                        String password = getPassword();

                        if (!contextManager.unlockKey(ks, alias, password)) {
                            JOptionPane.showMessageDialog(
                                    null,
                                    new String[] {
                                        Constant.messages.getString(
                                                "options.cert.error.accesskeystore")
                                    },
                                    Constant.messages.getString("options.cert.error"),
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } catch (Exception e) {
                    showKeyStoreCertError(e.toString());
                }
            }
            Certificate cert = contextManager.getCertificate(ks, alias);
            try {
                contextManager.getFingerPrint(cert);
            } catch (KeyStoreException kse) {
                showCertError("options.cert.error.fingerprint", kse.toString());
            }

            try {
                contextManager.setDefaultKey(ks, alias);

                OptionsParamCertificate certParam =
                        Model.getSingleton().getOptionsParam().getCertificateParam();
                certParam.setActiveCertificate();

            } catch (KeyStoreException e) {
                logger.error(e.getMessage(), e);
            }
            certificateTextField.setText(contextManager.getDefaultKey());
        }
    }

    public String getPassword() {
        JPasswordField askPasswordField = new JPasswordField();
        int result =
                JOptionPane.showConfirmDialog(
                        this,
                        askPasswordField,
                        Constant.messages.getString("options.cert.label.enterpassword"),
                        JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            return new String(askPasswordField.getPassword());
        } else return null;
    }

    private void deleteButtonActionPerformed(
            java.awt.event.ActionEvent evt) { // GEN-FIRST:event_deleteButtonActionPerformed
        int index = keyStoreList.getSelectedIndex();
        if (index >= 0) {
            boolean isDefaultKeyStore = contextManager.removeKeyStore(index);
            if (isDefaultKeyStore) {
                certificateTextField.setText("");
            }
            keyStoreListModel.removeElementAt(keyStoreList.getSelectedIndex());
            aliasTableModel.removeKeystore();
        }
    } // GEN-LAST:event_deleteButtonActionPerformed

    private void useClientCertificateCheckBoxActionPerformed(
            java.awt.event.ActionEvent
                    evt) { // GEN-FIRST:event_useClientCertificateCheckBoxActionPerformed
        // The enable unsafe SSL renegotiation checkbox is independent of using a client certificate
        // (although commonly related)
        // enableUnsafeSSLRenegotiationCheckBox.setEnabled(useClientCertificateCheckBox.isSelected());

        // keyStore tab
        certificatejTabbedPane.setEnabled(useClientCertificateCheckBox.isSelected());

        keyStoreScrollPane.setEnabled(useClientCertificateCheckBox.isSelected());
        keyStoreList.setEnabled(useClientCertificateCheckBox.isSelected());

        aliasScrollPane.setEnabled(useClientCertificateCheckBox.isSelected());
        aliasTable.setEnabled(useClientCertificateCheckBox.isSelected());

        deleteButton.setEnabled(useClientCertificateCheckBox.isSelected());
        setActiveButton.setEnabled(useClientCertificateCheckBox.isSelected());
        showAliasButton.setEnabled(useClientCertificateCheckBox.isSelected());

        // pkcs12 tab
        fileTextField.setEnabled(useClientCertificateCheckBox.isSelected());
        browseButton.setEnabled(useClientCertificateCheckBox.isSelected());
        pkcs12PasswordField.setEnabled(useClientCertificateCheckBox.isSelected());
        addPkcs12Button.setEnabled(useClientCertificateCheckBox.isSelected());

        // pkcs11 tab
        driverComboBox.setEnabled(useClientCertificateCheckBox.isSelected());
        driverButton.setEnabled(useClientCertificateCheckBox.isSelected());
        pkcs11PasswordField.setEnabled(useClientCertificateCheckBox.isSelected());
        addPkcs11Button.setEnabled(useClientCertificateCheckBox.isSelected());
        usePkcs11ExperimentalSliSupportCheckBox.setEnabled(
                useClientCertificateCheckBox.isSelected());
        usePkcs11ExperimentalSliSupportCheckBox.setSelected(
                Model.getSingleton()
                        .getOptionsParam()
                        .getExperimentalFeaturesParam()
                        .isExperimentalSliSupportEnabled());

        // actual certificate fields
        certificateTextField.setEnabled(useClientCertificateCheckBox.isSelected());
        showActiveCertificateButton.setEnabled(useClientCertificateCheckBox.isSelected());
    } // GEN-LAST:event_useClientCertificateCheckBoxActionPerformed

    private void usePkcs11ExperimentalSliSupportCheckBoxActionPerformed(
            java.awt.event.ActionEvent evt) {
        Model.getSingleton()
                .getOptionsParam()
                .getExperimentalFeaturesParam()
                .setSlotListIndexSupport(usePkcs11ExperimentalSliSupportCheckBox.isSelected());
    }

    // TODO remove
    private OptionsCertificatePanel getContentPane() {
        return this;
    }

    @Override
    public void initParam(Object obj) {
        OptionsParam options = (OptionsParam) obj;
        OptionsParamCertificate certParam = options.getCertificateParam();

        // Should only run once after startup if client certificate is set from commandline
        if (overrideEnableClientCertificate) {
            certParam.setEnableCertificate(true);
            overrideEnableClientCertificate = false;
        }
        keyStoreListModel.clear();
        for (int i = 0; i < contextManager.getKeyStoreCount(); i++) {
            keyStoreListModel.addElement(contextManager.getKeyStoreDescription(i));
        }
        Certificate cert = contextManager.getDefaultCertificate();
        if (cert != null) {
            certificateTextField.setText(cert.toString());
        }
        useClientCertificateCheckBox.setSelected(certParam.isUseClientCert());
        useClientCertificateCheckBoxActionPerformed(null);

        // getBtnLocation().setEnabled(getChkUseClientCertificate().isSelected());
        // getTxtLocation().setText(options.getCertificateParam().getClientCertLocation());
    }

    @Override
    public void saveParam(Object obj) throws Exception {
        OptionsParam options = (OptionsParam) obj;
        OptionsParamCertificate certParam = options.getCertificateParam();
        certParam.setEnableCertificate(useClientCertificateCheckBox.isSelected());
    }

    @Override
    public String getHelpIndex() {
        // ZAP: added help index
        return "ui.dialogs.options.certificate";
    }
} //  @jve:decl-index=0:visual-constraint="10,10"
