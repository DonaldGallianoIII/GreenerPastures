package com.greenerpastures.studio;

import com.greenerpastures.client.ui.DaemonController;
import com.greenerpastures.client.ui.DaemonView;
import com.greenerpastures.client.ui.NotebookView;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Design Studio — PNG mode. Renders the Daemon OUTSIDE Minecraft using the exact same paint code
 * ({@link DaemonView#paint}) driven by the shared {@link DaemonController}. Headless; writes a PNG.
 * For the interactive window (drag/wire/zoom), see {@link StudioLive}.
 *
 * <p>Run: {@code ./gradlew studio}  (optional {@code -Pout=path.png}). No Minecraft on the classpath.
 */
public final class StudioMain {
    private StudioMain() {}

    public static void main(String[] args) throws Exception {
        final int W = 960, H = 620;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        DaemonController ctrl = StudioData.demoController();
        ctrl.setViewport(W, H, NotebookView.CHROME_TOP);
        DaemonView.Model m = ctrl.buildModel();
        NotebookView.paint(new Java2DGpCanvas(g), m, StudioData.frame(), W, H);
        g.dispose();

        String out = args.length > 0 ? args[0] : "studio-daemon.png";
        File f = new File(out).getAbsoluteFile();
        ImageIO.write(img, "png", f);
        System.out.println("[studio] wrote " + f + " (" + W + "x" + H + ")");
    }
}
