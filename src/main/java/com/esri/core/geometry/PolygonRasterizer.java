package com.esri.core.geometry;

import xyz.tms.TmsTile;


/**
 * Package-private members can only be accessed from here
 *
 * Created by willtemperley@gmail.com on 20-Oct-16.
 */
public class PolygonRasterizer {

    SimpleRasterizer rasterizer = new SimpleRasterizer();
    final Transformation2D trans = new Transformation2D();

    private static int width = 256;
    private static int height = 256;

    private static class ScanCallback implements SimpleRasterizer.ScanCallback {

        private final int width;
        private final int height;
        private final int[] matrix;
        private int burnValue = getPackedRGBA(128, 128, 128, 128); //grey

        public void setBurnValue(int burnValue) {
            this.burnValue = burnValue;
        }


        ScanCallback(int width, int height) {
            this.width = width;
            this.height = height;
            this.matrix = new int[width * height];
            int background = getPackedRGBA(128, 0, 0, 0); //black
            for (int i = 0; i < matrix.length; i++) {
                matrix[i] = background;
            }
        }

        @Override
        public void drawScan(int[] scans, int scanCount) {


            /*
            Scanlines come in triplets - x1, x2 and the y coord
            Need to  burn values between x1 and x2 on the inverted y coordinate
             */
            for (int i = 0; i < scans.length; i+=3) {
                int y = PolygonRasterizer.height - scans[i+2] - 1;// image coordinates
                int offset = y * width;

                for (int scanLineX = scans[i]; scanLineX < scans[i+1]; scanLineX++) {
                    matrix[offset + scanLineX] = burnValue;
                }
            }

        }

        public int getPackedRGBA(int a, int r, int g, int b) {
            return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
        }

        public byte[] getImage() {
            return BinaryImage.getImage(matrix, width, height);
        }
    }

    public void fillEnv(Polygon polygon) {
        Envelope2D env = new Envelope2D();
        polygon.queryEnvelope2D(env);

        ScanCallback scanCallBack = new ScanCallback(width, height);

        rasterizer.setup(width, height, scanCallBack);

        rasterizer.fillEnvelope(env);
        rasterizer.flush();
    }

    public byte[] rasterizeTile(Polygon polygon, TmsTile tmsTile) {

        trans.setIdentity(); //reset
        Envelope2D envelope2D = tmsTile.getEnvelope();

        double xx = -envelope2D.xmin;
        double yy = -envelope2D.ymin;
        trans.shift(xx, yy);
        double scale = tmsTile.getScale();
        trans.scale(scale, scale);

        Envelope2D env = new Envelope2D();
        polygon.queryEnvelope2D(env);

        ScanCallback scanCallBack = new ScanCallback(width, height);
        rasterizer.setup(width, height, scanCallBack);

        SegmentIterator segIter = polygon.querySegmentIterator();
        Point2D p1 = new Point2D();
        Point2D p2 = new Point2D();
        while (segIter.nextPath()) {
            while (segIter.hasNextSegment()) {
                Segment seg = segIter.nextSegment();
                if (seg.getType() != Geometry.Type.Line)
                    throw GeometryException.GeometryInternalError(); // TODO:
                trans.transform(seg.getStartXY(), p1);
                trans.transform(seg.getEndXY(), p2);
                rasterizer.addEdge(p1.x, p1.y, p2.x, p2.y);
            }
        }

        rasterizer.renderEdges(SimpleRasterizer.EVEN_ODD);

        rasterizer.flush();

        return scanCallBack.getImage();
    }

}
