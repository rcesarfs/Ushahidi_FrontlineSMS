/* Copyright 2008 Robert Bajzat. All rights reserved. GPL v3. Use is subject to license terms. */
package com.ushahidi.plugins.mapping.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.ImageObserver;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import thinlet.CustomComponent;

import com.ushahidi.plugins.mapping.maps.TiledMap;
import com.ushahidi.plugins.mapping.maps.MapFactory;
import com.ushahidi.plugins.mapping.maps.geo.Location;
import com.ushahidi.plugins.mapping.maps.providers.MapProvider;
import com.ushahidi.plugins.mapping.maps.providers.openstreetmap.OpenStreetMapProvider;
import com.ushahidi.plugins.mapping.maps.providers.offline.OfflineProvider;
import com.ushahidi.plugins.mapping.ui.markers.Marker;
import com.ushahidi.plugins.mapping.util.MappingLogger;

/**
 * MapBean
 * @author dalezak
 *
 */
public class MapBean extends CustomComponent implements ImageObserver {

	private static final long serialVersionUID = 1L;

	public static MappingLogger LOG = MappingLogger.getLogger(MapBean.class);
	
    private TiledMap map = null;

    private Image image = null;
    private final List<MapListener> mapListeners = new ArrayList<MapListener>();
    private Dimension dimensions;

    private MapDragListener mouseListener = new MapDragListener();

    private MapProvider mapProvider;
    
    /** location bounds for the region covered by the map */
    private Location location;
    /** Markers to be plotted on the map */
    private final List<Marker> markers = new ArrayList<Marker>();
    /** Instance of the UI controller for the mapping plugin */
    /** Name of the file containing the offline maps*/
    private String offlineMapFile;

    private static final int DEFAULT_ZOOM_LEVEL = 7;
    private static final int DEFAULT_POINT_SIZE = 18;
    private int pointSize = DEFAULT_POINT_SIZE;
    private int zoomLevel = DEFAULT_ZOOM_LEVEL;

    public MapBean() {
        addMouseListener(mouseListener);
        addMouseMotionListener(mouseListener);
        addMouseWheelListener(mouseListener);
    }
    
    public void setMapProvider(MapProvider mapProvider) {
    	this.mapProvider = mapProvider;
    	destroyMap();
    	map = null;
    	repaint();
    }
    
    public MapProvider getMapProvider() {
    	if (this.mapProvider == null) {
    		//Default Map Provider is Open Street Map
    		this.mapProvider = new OpenStreetMapProvider();
    	}
    	return this.mapProvider;
    }

    public void setOfflineMapFile(String fileName){
        this.offlineMapFile = fileName;
    }

    public synchronized void clearMarkers(boolean repaint) {
    	this.markers.removeAll(this.markers);
    	if (repaint) {
    		repaint();
    	}
    }
    
    public synchronized void addMarker(Marker marker, boolean repaint) {
    	this.markers.add(marker);
    	if (repaint) {
    		repaint();
    	}
    }
    
    /**
     * Initializes the static location property
     * 
     * @param longitude Longitude of the area covered
     * @param latitude Latitude of the are covered
     */
    public synchronized void setLocation(double longitude, double latitude){		
        if(location != null){
            map = null;
            location = new Location(latitude, longitude);
            repaint();
        }
        else{
            location = new Location(latitude, longitude);
        }
    }
    
    public synchronized void setLocationAndZoomLevel(double longitude, double latitude, int zoom){		
        if(location != null){
            map = null;
            zoomLevel = zoom;
            location = new Location(latitude, longitude);
            repaint();
        }
        else{
        	zoomLevel = zoom;
            location = new Location(latitude, longitude);
        }
    }

    public Dimension getPreferredSize() {
        return new Dimension(640, 640);
    }

    private boolean isResized() {
        return dimensions != getSize();
    }

    public TiledMap getMap() {
        return map;
    }	

    public Point getCursorPosition(){
        return new Point(mouseListener.mouseCoords.x, mouseListener.mouseCoords.y);
    }

    public void paint(Graphics graphic) {
        if (dimensions == null) {
            dimensions = getSize();
        }
        if(location == null){
            return;
        }
        if (map == null && location != null) {
            if(offlineMapFile == null){
                map = MapFactory.mapByCenterZoom(this.getMapProvider(), location, zoomLevel, dimensions);
            }
            else{
                try {
                    map = MapFactory.mapByCenterZoom(new OfflineProvider(offlineMapFile), location, zoomLevel, dimensions);
                } catch (IOException e) {
                    LOG.debug(e);
                }				
            }
            map.setObserver(this);
            image = map.draw();
        }
        if(isResized()) {
            dimensions = getSize();
            if (map != null) {
            	map.resize(dimensions.width, dimensions.height);
                image = map.draw();	
            }
        }
        graphic.drawImage(image, 0, 0, null);	
        plotMarkers(graphic);	
    }

    private void plotMarkers(Graphics g){
		for(Marker marker : this.markers) {
			if (marker.getLocation() != null) {
				double latitude = marker.getLocation().getLatitude();
        		double longitude = marker.getLocation().getLongitude();
        		Point point = map.locationPoint(new Location(latitude, longitude));
        		g.drawImage(marker.getImage(), point.x, point.y, pointSize, pointSize, this);
			}
		}
    }
    
    public synchronized void addMapListener(MapListener listener) {
    	this.mapListeners.add(listener);
    }

    public synchronized void removeMapListener(MapListener listener) {
    	this.mapListeners.remove(listener);
    }
    
    public synchronized void removeMapListeners() {
    	this.mapListeners.removeAll(this.mapListeners);
    }

    public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
        repaint();
        return true;
    }

    public void setZoomLevel(int zoomLevel){
    	this.zoomLevel = zoomLevel;
        LOG.debug("Zoome Lavel = %d", zoomLevel);
        pointSize = (zoomLevel < map.getZoom()) ? pointSize - 2 : 
            ((zoomLevel >= DEFAULT_ZOOM_LEVEL)? DEFAULT_POINT_SIZE : pointSize + 2);
        map.zoomTo(zoomLevel, new Point(this.getWidth() / 2, this.getHeight() / 2));
        map.draw();
        repaint();
    }

    //>	HELPERS
    /**
     * @see {@link TiledMap#getZoom()}
     */
    public int getZoomLevel(){
        return map.getZoom();
    }

    /**
     * Shuts down the map
     */
    public void destroyMap(){
        if(map != null) {
            map.destroy();
        }
    }

    /**
     * Private class to handle the map dragging events
     * Credit To: http://mappanel.sourceforge.net 
     */
    private class MapDragListener extends MouseAdapter implements MouseMotionListener, MouseWheelListener{
    //> PROPERTIES
        /** Saves the current screen coordinates when the mouse is moved or dragged */
        private Point mouseCoords;
        /** Saves the current screen coordinates when the mouse is clicked or pressed */
        private Point downCoords;
        
    //> CONSTRUCTOR
        public MapDragListener(){
            mouseCoords = new Point();
        }

        public void mouseClicked(MouseEvent e){
//        	if (map != null) {
//        		int zoom = map.getZoom() + 1;
//                setZoomLevel(zoom);
//                for (MapListener mapListener : mapListeners) {
//                	mapListener.mapZoomed(zoom);
//                }	
//        	}
        }

        public void mousePressed(MouseEvent event){
            if(event.getClickCount() >= 2){
                mouseClicked(event);
                return;
            }
            downCoords = event.getPoint();
           for (MapListener mapListener : mapListeners) {
            	if (map != null && mapListener != null && event.getPoint() != null) {
            		Location location = map.pointLocation(event.getPoint());
            		if (location != null) {
            			 mapListener.locationSelected(location.lat, location.lon);	
            		}
                }
            }	
        }

        public void mouseReleased(MouseEvent event){            
            downCoords = null;
        }

        public void mouseMoved(MouseEvent event){
            handlePosition(event);
        }

        public void mouseDragged(MouseEvent event){
            handlePosition(event);
            handleDrag(event);
        }

        public void mouseExited(MouseEvent event){}

        public void mouseEntered(MouseEvent event){
            super.mouseEntered(event);
        }

        public void mouseWheelMoved(MouseWheelEvent event){}

        /**
         * Saves the current position of the mouse on the map canvas for purposes
         * of updating the coordinate display on the UI
         * 
         * @param event
         */
        private void handlePosition(MouseEvent event){
            mouseCoords = event.getPoint();
            for (MapListener mapListener : mapListeners) {
            	if (map != null && mapListener != null && event.getPoint() != null) {
            		Location location = map.pointLocation(event.getPoint());
            		if (location != null) {
            			 mapListener.locationHovered(location.lat, location.lon);	
            		}
                }
            }
        }

        /**
         * Drags the map across the canvas
         * A drag is only initiated if the (x,y) values in @param e are different
         * from the ones in {@link #downCoords}
         * 
         * @param event {@link java.awt.event.MouseEvent} reference
         */
        private void handleDrag(MouseEvent event){
            if(downCoords != null){
                int tx = downCoords.x - event.getX();
                int ty = downCoords.y - event.getY();
                
                // Save the current position to prevent extra dragging
                downCoords = event.getPoint();
                
                // Only pan the map if the change is non-zero
                if(tx != 0 && ty != 0){
                    //LOG.debug("Panning map");
                	if (map != null) {
                		map.panBy(tx, ty);
                	}
                    repaint();
                }
            }
        }
    }
}
