package forge.screens.home;

import java.awt.BorderLayout;
import java.io.File;

import javax.swing.JPanel;

import forge.localinstance.properties.ForgeConstants;

/**
 * Hosts the HTML redesign (forge.standalone.html) inside a JavaFX WebView so the
 * desktop app renders the exact Forge.dc.html design. Construction initializes the
 * JavaFX toolkit; callers should catch Throwable and fall back if JavaFX is absent.
 */
@SuppressWarnings("serial")
public class WebDesignScreen extends JPanel {
    private final javafx.embed.swing.JFXPanel fxPanel = new javafx.embed.swing.JFXPanel();

    public WebDesignScreen(final Runnable onError) {
        setLayout(new BorderLayout());
        add(fxPanel, BorderLayout.CENTER);
        javafx.application.Platform.setImplicitExit(false);

        final File html = new File(ForgeConstants.RES_DIR + "redesign" + File.separator + "forge.standalone.html");
        final String url = html.toURI().toString();

        javafx.application.Platform.runLater(() -> {
            try {
                final javafx.scene.web.WebView web = new javafx.scene.web.WebView();
                web.setContextMenuEnabled(false);
                web.getEngine().load(url);
                final javafx.scene.Scene scene = new javafx.scene.Scene(web);
                fxPanel.setScene(scene);
            } catch (final Throwable t) {
                System.err.println("WebView init failed, falling back to Command Center: " + t);
                if (onError != null) {
                    javax.swing.SwingUtilities.invokeLater(onError);
                }
            }
        });
    }
}
