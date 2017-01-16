package com.defano.jmonet.tools;


import com.defano.jmonet.model.PaintToolType;
import com.defano.jmonet.tools.base.PaintTool;

import java.awt.*;
import java.awt.event.MouseEvent;

public class MagnifierTool extends PaintTool {

    private double scale = 1.0;
    private double magnificationStep = 2;

    public MagnifierTool() {
        super(PaintToolType.MAGNIFIER);
    }

    @Override
    public void mousePressed(MouseEvent e, int scaleX, int scaleY) {

        if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) {
            reset();
        }

        else if (e.isShiftDown()) {
            zoomOut(e.getX(), e.getY());
            recenter(e.getX(), e.getY(), scaleX, scaleY);
        }

        else {
            zoomIn();
            recenter(e.getX(), e.getY(), scaleX, scaleY);
        }
    }

    @Override
    public void activate(com.defano.jmonet.canvas.Canvas canvas) {
        super.activate(canvas);
        this.scale = canvas.getScaleProvider().get();
    }

    private void recenter(int canvasX, int canvasY, int imageX, int imageY) {
        getCanvas().setImageLocation(new Point((int)(imageX * scale) - canvasX, (int)(imageY * scale) - canvasY));
    }

    private void reset() {
        scale = 1.0;
        getCanvas().setScale(scale);
        getCanvas().setImageLocation(new Point(0,0));
    }

    private void zoomIn() {
        scale += magnificationStep;
        getCanvas().setScale(scale);
    }

    private void zoomOut(int x, int y) {
        scale -= magnificationStep;

        if (scale <= 1.0) {
            reset();
            return;
        }

        getCanvas().setScale(scale);
    }
}
