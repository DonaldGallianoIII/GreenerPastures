package com.greenerpastures.studio;

import com.greenerpastures.client.ui.DaemonController;
import com.greenerpastures.client.ui.DaemonController.Unit;
import com.greenerpastures.client.ui.DaemonView;
import com.greenerpastures.client.ui.NotebookView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Fake-but-representative data for the Design Studio (no Minecraft) — shared by the PNG + live modes. */
public final class StudioData {
    private StudioData() {}

    /** Interactive part: tethered units (two pre-wired pairs) the user can drag/wire. */
    public static DaemonController demoController() {
        String[] species = {"Ditto", "Charmander", "Gible", "Eevee"};
        List<Unit> units = new ArrayList<>();
        for (int i = 0; i < species.length; i++) {
            units.add(new Unit(UUID.randomUUID(), species[i], species[i], (i / 2) + 1));   // pairs 1 & 2
        }
        DaemonController c = new DaemonController(units, 5, "Shiny Farm");
        c.initView(16, 100, 0.92);
        return c;
    }

    /** Static downstream-pipeline preview added to the rendered model: eggs → augment → IV filter → keep/void. */
    public static void decorate(DaemonView.Model m) {
        DaemonView.Node aug = DaemonView.Node.augment(60, 360, "S", "Shiny Boost", new String[]{"+30% shiny proc"});
        int[][] ivs = {{31, 31}, {0, 31}, {31, 31}, {31, 31}, {0, 31}, {31, 31}};
        DaemonView.Node fil = DaemonView.Node.filter(290, 336, ivs, "Adamant", "any");
        DaemonView.Node col = DaemonView.Node.collection(520, 340, 341);
        DaemonView.Node vd  = DaemonView.Node.voidBin(520, 432, 1289);
        m.nodes.add(aug);
        m.nodes.add(fil);
        m.nodes.add(col);
        m.nodes.add(vd);
        m.wires.add(new DaemonView.Wire(aug.x + aug.w(), aug.y + 33, fil.x, fil.y + 33, DaemonView.FLOW));
        m.wires.add(new DaemonView.Wire(fil.x + fil.w(), fil.y + 40, col.x, col.y + 30, DaemonView.PASS));
        m.wires.add(new DaemonView.Wire(fil.x + fil.w(), fil.y + fil.h() - 22, vd.x, vd.y + 30, DaemonView.VOIDC));
    }

    public static NotebookView.Frame frame() {
        NotebookView.Frame f = new NotebookView.Frame();
        f.pastureName = "Shiny Farm";
        f.kernelTier = "Diamond Kernel";
        f.kernelAugments = new String[]{"shiny-boost==1.0"};
        f.threads = "2/5 threads";
        return f;
    }
}
