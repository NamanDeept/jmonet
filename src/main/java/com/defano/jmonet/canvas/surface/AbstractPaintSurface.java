package com.defano.jmonet.canvas.surface;

import com.defano.jmonet.canvas.layer.ScaledLayeredImage;
import com.defano.jmonet.canvas.observable.SurfaceInteractionObserver;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A surface that paints a layered image.
 */
public abstract class AbstractPaintSurface extends JComponent implements PaintSurface, KeyListener, MouseListener,
        MouseMotionListener, KeyEventDispatcher, ScaledLayeredImage {

    private final static Color CLEAR_COLOR = new Color(0, 0, 0, 0);
    private final BehaviorSubject<Double> scaleSubject = BehaviorSubject.createDefault(1.0);
    private final List<SurfaceInteractionObserver> interactionListeners = new ArrayList<>();
    private Dimension surfaceDimension = new Dimension();
    private double scanlineThreadhold = 6.0;
    private Color scanlineColor = new Color(0xF5, 0xF5, 0xF5);
    private AlphaComposite scanlineComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER);
    private SurfaceScrollController surfaceScrollController = new DefaultSurfaceScrollController(this);

    /**
     * Creates a paint surface with the specified dimensions. Note that this dimension refers to the size of the
     * "paintable" space, which is not the same as the size of the Swing component (the surface dimension may be larger,
     * smaller, or the same size as this Swing component).
     *
     * @param surfaceDimension The size of the paintable surface.
     */
    public AbstractPaintSurface(Dimension surfaceDimension) {
        super();

        Dimension scaledDimension = scaleDimension(surfaceDimension);

        setMaximumSize(scaledDimension);
        setPreferredSize(scaledDimension);
        setSize(scaledDimension);

        setOpaque(false);
        setLayout(null);
        setFocusable(true);

        addMouseListener(this);
        addMouseMotionListener(this);

        setSurfaceDimension(surfaceDimension);

        // Adding a KeyListener to this component won't always work the way the user expects; this is cheating, but
        // it assures paint tools get all key events, regardless of the component hierarchy we may be embedded in.
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    }

    public abstract Paint getCanvasBackground();

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSurfaceDimension(Dimension surfaceDimensions) {
        this.surfaceDimension = new Dimension(surfaceDimensions.width, surfaceDimensions.height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getSurfaceDimension() {
        return surfaceDimension;
    }

    /**
     * Gets the dimensions of the surface multiplied by the surface's scale factor.
     *
     * @return The scaled surface dimensions.
     */
    public Dimension getScaledSurfaceDimension() {
        return scaleDimension(surfaceDimension);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getScale() {
        return scaleSubject.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setScale(double scale) {

        if (scale > 1) {
            scale = Math.round(scale);
        }

        double prevScale = 1.0;
        Rectangle prevScrollRect = new Rectangle();
        SurfaceScrollController scrollController = getSurfaceScrollController();

        // Save the current scale and scroll position
        if (scrollController != null) {
            prevScale = getScale();
            prevScrollRect = getSurfaceScrollController().getScrollRect();
        }

        // Change scale
        scaleSubject.onNext(scale);
        Dimension scaledDimension = scaleDimension(surfaceDimension);
        setPreferredSize(scaledDimension);
        setMaximumSize(scaledDimension);

        // Modify scroll position to "zoom in" or "zoom out" on the center of the previous scroll view bounds
        if (scrollController != null) {
            scrollController.setScrollPosition(new Point(
                    Math.max(0, (int) ((prevScrollRect.x + prevScrollRect.width / 2) * scale / prevScale - prevScrollRect.width / 2)),
                    Math.max(0, (int) ((prevScrollRect.y + prevScrollRect.height / 2) * scale / prevScale - prevScrollRect.height / 2))
            ));
        }

        revalidate();
        repaint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Observable<Double> getScaleObservable() {
        return scaleSubject;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getScanlineScaleThreadhold() {
        return scanlineThreadhold;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setScanlineScaleThreadhold(double scanlineThreadhold) {
        this.scanlineThreadhold = scanlineThreadhold;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Color getScanlineColor() {
        return scanlineColor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setScanlineColor(Color scanlineColor) {
        this.scanlineColor = scanlineColor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AlphaComposite getScanlineComposite() {
        return scanlineComposite;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setScanlineComposite(AlphaComposite scanlineComposite) {
        this.scanlineComposite = scanlineComposite;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        surfaceScrollController = null;
        scaleSubject.onComplete();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
        removeAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Rectangle clip = g.getClipBounds();

        if (clip != null && !clip.isEmpty() && isVisible()) {

            // Draw visible portion of this surface's image into a buffer
            BufferedImage buffer = new BufferedImage(clip.width, clip.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = buffer.createGraphics();
            g2d.setBackground(CLEAR_COLOR);
            g2d.clearRect(clip.x, clip.y, clip.width, clip.height);
            paint(g2d, getScale(), clip);
            paintScanlines(g2d, getScaledSurfaceDimension());
            g2d.dispose();

            // Draw the surface background
            if (getCanvasBackground() != null) {
                ((Graphics2D) g).setPaint(getCanvasBackground());
                g.fillRect(clip.x, clip.y, clip.width, clip.height);
            }

            // Draw the paint image
            g.drawImage(buffer, clip.x, clip.y, null);
        }

        // DO NOT dispose the graphics context in this method.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSurfaceInteractionObserver(SurfaceInteractionObserver listener) {
        interactionListeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeSurfaceInteractionObserver(SurfaceInteractionObserver listener) {
        return interactionListeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void keyTyped(KeyEvent e) {
        for (SurfaceInteractionObserver thisListener : interactionListeners.toArray(new SurfaceInteractionObserver[]{})) {
            thisListener.keyTyped(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void keyPressed(KeyEvent e) {
        for (SurfaceInteractionObserver thisListener : interactionListeners.toArray(new SurfaceInteractionObserver[]{})) {
            thisListener.keyPressed(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void keyReleased(KeyEvent e) {
        for (SurfaceInteractionObserver thisListener : interactionListeners.toArray(new SurfaceInteractionObserver[]{})) {
            thisListener.keyReleased(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void mouseClicked(MouseEvent e) {
        requestFocus();

        for (SurfaceInteractionObserver thisListener : interactionListeners.toArray(new SurfaceInteractionObserver[]{})) {
            thisListener.mouseClicked(e, convertViewPointToModel(e.getPoint()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void mousePressed(MouseEvent e) {
        for (SurfaceInteractionObserver thisListener : interactionListeners.toArray(new SurfaceInteractionObserver[]{})) {
            thisListener.mousePressed(e, convertViewPointToModel(e.getPoint()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void mouseReleased(MouseEvent e) {
        for (SurfaceInteractionObserver thisListener : interactionListeners.toArray(new SurfaceInteractionObserver[]{})) {
            thisListener.mouseReleased(e, convertViewPointToModel(e.getPoint()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void mouseEntered(MouseEvent e) {
        for (SurfaceInteractionObserver thisListener : interactionListeners.toArray(new SurfaceInteractionObserver[]{})) {
            thisListener.mouseEntered(e, convertViewPointToModel(e.getPoint()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void mouseExited(MouseEvent e) {
        for (SurfaceInteractionObserver thisListener : interactionListeners.toArray(new SurfaceInteractionObserver[]{})) {
            thisListener.mouseExited(e, convertViewPointToModel(e.getPoint()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void mouseDragged(MouseEvent e) {
        for (SurfaceInteractionObserver thisListener : interactionListeners.toArray(new SurfaceInteractionObserver[]{})) {
            thisListener.mouseDragged(e, convertViewPointToModel(e.getPoint()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void mouseMoved(MouseEvent e) {
        for (SurfaceInteractionObserver thisListener : interactionListeners.toArray(new SurfaceInteractionObserver[]{})) {
            thisListener.mouseMoved(e, convertViewPointToModel(e.getPoint()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addComponent(Component component) {
        add(component);

        revalidate();
        repaint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeComponent(Component component) {
        remove(component);

        revalidate();
        repaint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        switch (e.getID()) {
            case KeyEvent.KEY_TYPED:
                AbstractPaintSurface.this.keyTyped(e);
                break;
            case KeyEvent.KEY_PRESSED:
                AbstractPaintSurface.this.keyPressed(e);
                break;
            case KeyEvent.KEY_RELEASED:
                AbstractPaintSurface.this.keyReleased(e);
                break;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SurfaceScrollController getSurfaceScrollController() {
        return surfaceScrollController;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSurfaceScrollController(SurfaceScrollController surfaceScrollController) {
        this.surfaceScrollController = surfaceScrollController;
    }

}
