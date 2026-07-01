package forge.screens.home;

import java.awt.Insets;

import javax.swing.SwingUtilities;

import forge.toolbox.FButton;
import forge.toolbox.FSkin;
import forge.util.Localizer;

@SuppressWarnings("serial")
public class StartButton extends FButton {
    public StartButton() {
        super(Localizer.getInstance().getMessage("lblStart"));
        setPrimary(true);
        setFont(FSkin.getBoldFont(20));
        setMargin(new Insets(8, 46, 8, 46));
        // Accessible name.
        this.getAccessibleContext().setAccessibleName("Start game");

        addActionListener(e -> {
            setEnabled(false);
            // ensure the click action can resolve before we allow the button to be clicked again
            SwingUtilities.invokeLater(() -> setEnabled(true));
        });
    }
}
