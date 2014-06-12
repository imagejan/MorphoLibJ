/**
 * 
 */
package inra.ijpb.measure;

import static java.lang.Math.sqrt;
import ij.IJ;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import inra.ijpb.morphology.LabelImages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * Provides a set of static methods to compute geometric measures such as area,
 * perimeter, and their densities in planar binary or label images.
 * 
 * @author David Legland
 *
 */
public class GeometricMeasures2D {
	
    // ====================================================
    // Class constants 

	public static boolean debug = false;
	

	// ====================================================
    // Main processing functions 

    /**
	 * Computes the area for each particle in the label image, taking into account image resolution.
	 * @see inra.ijpb.morphology.LabelImages#pixelCount(ij.process.ImageProcessor, int[])
	 */
    public static final double[] area(ImageProcessor image, int[] labels, double[] resol) {
        // pre-compute the area of individual voxel
        if (resol == null || resol.length != 2) {
        	throw new IllegalArgumentException("Resolution must be a double array of length 2");
        }
        double pixelArea = resol[0] * resol[1];
        
        // initialize result
		int nLabels = labels.length;
		double[] areas = new double[nLabels];

		// First count the number of pixels in each region
	    int[] counts = LabelImages.pixelCount(image, labels);

	    // convert pixel count to areas
	    for (int i = 0; i < areas.length; i++) {
	    	areas[i] = counts[i] * pixelArea;
	    }
	    
	    return areas;
	}

	/**
	 * Counts the number of pixel that composes the particle with given label. 
	 */
    public static final int particleArea(ImageProcessor image, int label) {
	    int width 	= image.getWidth();
	    int height 	= image.getHeight();
	
	    int count = 0;
	
	    // count all pixels belonging to the particle
	    for (int y = 0; y < height; y++)
	        for (int x = 0; x < width; x++)
	            if (image.get(x, y) == label)
	            	count++;
	
	    return count;
	}

	/**
     * Computes perimeter of each label using Crofton method.  
     * 
     * @param labelImage the input image containing label of particles
     * @param nDirs the number of directions to process, either 2 or 4
     * @param resol the spatial resolution
     * @return a data table containing the perimeter of each labeled particle 
     */
	public static final ResultsTable croftonPerimeter(ImageProcessor labelImage,
			double[] resol, int nDirs) {
        // Check validity of parameters
        if (labelImage==null) return null;
        
        int[] labels = LabelImages.findAllLabels(labelImage);
        int nbLabels = labels.length;

        double[] areas = area(labelImage, labels, resol);
        double[] perims = croftonPerimeter(labelImage, labels, resol, nDirs);
        
        // Create data table
        ResultsTable table = new ResultsTable();
        for (int i = 0; i < nbLabels; i++) {
        	int label = labels[i];
        	
        	table.incrementCounter();
        	table.addLabel(Integer.toString(label));
        	
        	table.addValue("Area", areas[i]);
        	table.addValue("Perimeter", perims[i]);

        	// Also compute circularity (ensure value is between 0 and 1)
        	double p = perims[i];
        	double circu = Math.min(4 * Math.PI * areas[i] / (p * p), 1);
        	table.addValue("Circularity", circu);
        	table.addValue("Elong.", 1./circu);
        }
    	IJ.showStatus("");

        return table;
    }
    
	/**
	 * Compute surface area for each label given in the "labels" argument.
	 */
	public final static double[] croftonPerimeter(ImageProcessor image, int[] labels, double[] resol, int nDirs) {
        
        // create associative array to know index of each label
		int nLabels = labels.length;
        HashMap<Integer, Integer> labelIndices = new HashMap<Integer, Integer>();
        for (int i = 0; i < nLabels; i++) {
        	labelIndices.put(labels[i], i);
        }

        // pre-compute LUT corresponding to resolution and number of directions
		IJ.showStatus("Compute LUT...");
		double[] lut = computePerimeterLut(resol, nDirs);

		// initialize result
		double[] perimeters = new double[nLabels];

		// size of image
		int sizeX = image.getWidth();
		int sizeY = image.getHeight();
		
		// for each configuration of 2x2 pixels, we identify the labels
		ArrayList<Integer> localLabels = new ArrayList<Integer>(8);
		
		// iterate on image pixel configurations
		IJ.showStatus("Measure perimeter...");
		for (int y = 0; y < sizeY - 1; y++) {
			IJ.showProgress(y, sizeY);
			for (int x = 0; x < sizeX - 1; x++) {

				// identify labels in current config
				localLabels.clear();
				for (int y2 = y; y2 <= y + 1; y2++) {
					for (int x2 = x; x2 <= x + 1; x2++) {
						int label = image.get(x2, y2);
						// do not consider background
						if (label == 0)
							continue;
						// keep only one instance of each label
						if (!localLabels.contains(label))
							localLabels.add(label);
					}
				}

				// if no label in local configuration contribution is zero
				if (localLabels.size() == 0) {
					continue;
				}

				// For each label, compute binary confi
				for (int label : localLabels) {
					// Compute index of local configuration
					int index = 0;
					index += image.get(x, y) == label ? 1 : 0;
					index += image.get(x + 1, y) == label ? 2 : 0;
					index += image.get(x, y + 1) == label ? 4 : 0;
					index += image.get(x + 1, y + 1) == label ? 8 : 0;

					// retriev label index from label value
					int labelIndex = labelIndices.get(label);
					
					// update measure for current label
					perimeters[labelIndex] += lut[index];
				}
			}
		}
        
		IJ.showStatus("");
    	IJ.showProgress(1);
        return perimeters;
	}
	
	/**
	 * Computes the Look-up table that is used to compute perimeter.
	 */
	private final static double[] computePerimeterLut(double[] resol, int nDirs) {
		// distances between a pixel and its neighbors.
		// di refer to orthogonal neighbors
		// dij refer to diagonal neighbors 
		double d1 = resol[0];
		double d2 = resol[1];
		double d12 = Math.hypot(resol[0], resol[1]);
		double area = d1 * d2;

    	// weights associated to each direction, computed only for four directions
    	double[] weights = null;
    	if (nDirs == 4)
    		weights = computeDirectionWeightsD4(resol);
 
		// initialize output array (16 configurations in 2D)
		final int nConfigs = 16;
		double[] tab = new double[nConfigs];

		// loop for each tile configuration
		for (int i = 0; i < nConfigs; i++) {
		    // create the binary image representing the 2x2 tile
		    boolean[][] im = new boolean[2][2];
		    im[0][0] = (i & 1) > 0;
		    im[0][1] = (i & 2) > 0;
		    im[1][0] = (i & 4) > 0;
		    im[1][1] = (i & 8) > 0;
		    
	        // contributions for isothetic directions
		    double ke1, ke2;
		    
		    // contributions for diagonal directions
            double ke12;
            
            // iterate over the 4 pixels within the configuration
		    for (int y = 0; y < 2; y++) {
			    for (int x = 0; x < 2; x++) {
			    	if (!im[y][x])
			    		continue;
			    	
			    	// divides by two to convert intersection count to diameter
					ke1 = im[y][1 - x] ? 0 : (area / d1) / 2;
					ke2 = im[1 - y][x] ? 0 : (area / d2) / 2;
				    
				    if (nDirs == 2) {
				    	// Count only orthogonal directions
				    	// divides by two for average, and by two for multiplicity
			            tab[i] += (ke1 + ke2) / 4;
			            
				    } else if (nDirs == 4) {
				    	// compute contribution of diagonal directions
						ke12 = im[1 - y][1 - x] ? 0 : (area / d12) / 2;
			            
			            // Decomposition of Crofton formula on 4 directions, 
						// taking into account multiplicities
						tab[i] += ((ke1 / 2) * weights[0] 
								+ (ke2 / 2) * weights[1]
								+ ke12 * weights[2]);
				    } 
			    }
			    
		    }
		    
		    // Add a normalisation constant
		    tab[i] *= Math.PI;
		}
		
		return tab;		
	}
	 
    /**
     * Computes perimeter of each label using Crofton method with 2 directions.  
     */
    public static final double[] croftonPerimeterD2(ImageProcessor labelImage, 
    		int[] labels, double[] resol) {
        // Check validity of parameters
        if (labelImage==null) return null;
        
        int nbLabels = labels.length;

        // initialize result
        double[] perimeters = new double[nbLabels];
                            
        // image resolution in each orthogonal and diagonal directions
        double d1  = resol[0];
        double d2  = resol[1];

        for (int i = 0; i < nbLabels; i++) {
        	int label = labels[i];
        	IJ.showStatus("Compute perimeter of label: " + label);

        	// Count number of transitions in each direction
        	int n1 = countTransitionsD00(labelImage, label, true); 
        	int n2 = countTransitionsD90(labelImage, label, true);
        	
        	// Compute perimeter
        	perimeters[i] = (n1 * d2 + n2 * d1) * Math.PI / 4.0;
        	
        }
    	IJ.showStatus("");

        return perimeters;
    }
    
    /**
     * Computes perimeter of each label using Crofton method with 4 directions
     * (orthogonal and diagonal).  
     */
    public static final double[] croftonPerimeterD4(ImageProcessor labelImage, 
    		int[] labels,  double[] resol) {
        // Check validity of parameters
        if (labelImage==null) return null;
        
        int nbLabels = labels.length;

        // initialize result
        double[] perimeters = new double[nbLabels];
                            
        // image resolution in each orthogonal and diagonal directions
        double d1  = resol[0];
        double d2  = resol[1];
        double d12 = Math.hypot(d1, d2);

        // area of a pixel (used for computing line densities)
        double vol = d1 * d2;

    	// compute weights associated to each direction
    	double[] weights = computeDirectionWeightsD4(resol);

    	for (int i = 0; i < nbLabels; i++) {
        	int label = labels[i];
        	IJ.showStatus("Compute perimeter of label: " + label);
        	
        	// Count number of transitions in each direction
        	int n1 = countTransitionsD00(labelImage, label, true); 
        	int n2 = countTransitionsD90(labelImage, label, true);
        	int n3 = countTransitionsD45(labelImage, label, true); 
        	int n4 = countTransitionsD135(labelImage, label, true); 
        	
        	// Compute weighted diameters and multiplies by associated
        	// direction weights 
        	double wd1 = n1 * (vol / d1) * weights[0];
        	double wd2 = n2 * (vol / d2) * weights[1];
        	double wd3 = n3 * (vol / d12) * weights[2];
        	double wd4 = n4 * (vol / d12) * weights[3];

        	// Compute perimeter
        	perimeters[i] = (wd1 + wd2 + wd3 + wd4) * Math.PI / 2;
        	
        	if (debug) {
        		// Display individual counts
        		System.out.println(String.format(Locale.ENGLISH, "dir 1, n=%d, wd=%5.2f", n1, wd1));
        		System.out.println(String.format(Locale.ENGLISH, "dir 2, n=%d, wd=%5.2f", n2, wd2));
        		System.out.println(String.format(Locale.ENGLISH, "dir 3, n=%d, wd=%5.2f", n3, wd3));
        		System.out.println(String.format(Locale.ENGLISH, "dir 4, n=%d, wd=%5.2f", n4, wd4));
        	}
        }
    	IJ.showStatus("");

        return perimeters;
    }

	/**
	 * Computes porosity and perimeter density of binary image.  
	 * 
	 */
	public static final ResultsTable perimeterDensity(ImageProcessor image,
			double[] resol, int nDirs) {
	    if (nDirs == 2)
	    	return perimeterDensity_D2(image, resol);
	    else 
	    	return perimeterDensity_D4(image, resol);
	}

	/**
	 * Computes perimeter density of the binary image using Crofton method with 2
	 * directions (horizontal and vertical).
	 */
    private static final ResultsTable perimeterDensity_D2(ImageProcessor image, 
    		double[] resol) {

        // Create data table
        ResultsTable table = new ResultsTable();
        double perim;
                                     
        double d1  = resol[0];
        double d2  = resol[1];

        // area of a pixel (used for computing line densities)
        double pixelArea = d1 * d2;

    	// compute weights associated to each direction
    	double[] weights = computeDirectionWeightsD4(resol);

    	double area = particleArea(image, 255) * pixelArea;

    	// Count number of transitions in each direction
    	int n1 = countTransitionsD00(image, 255, false); 
    	int n2 = countTransitionsD90(image, 255, false);

    	// Compute weighted diameters and multiplies by associated
    	// direction weights 
    	double wd1 = n1 / d1  * weights[0];
    	double wd2 = n2 / d2  * weights[1];

    	// Compute perimeter
    	perim = (wd1 + wd2) * pixelArea * Math.PI / 2;

    	// Add new row in table
    	table.incrementCounter();

    	// area
    	table.addValue("Area", area);

    	// Compute porosity by dividing by observed area
    	int pixelCount = image.getWidth() * image.getHeight();
    	double refArea = pixelCount * pixelArea;
    	table.addValue("A. Density", area / refArea);

    	if (debug) {
    		// Display individual counts
    		table.addValue("N1", n1);
    		table.addValue("N2", n2);
    	}

    	// Display perimeter value
    	table.addValue("Perimeter", perim);

    	// compute perimeter density
		double refArea2 = (image.getWidth() - 1) * resol[0]
				* (image.getHeight() - 1) * resol[1];
		double perimDensity = perim / refArea2;
    	table.addValue("P. Density", perimDensity);

        return table;
    }

	/**
	 * Computes perimeter density of the binary image using Crofton method with 4
	 * directions (orthogonal and diagonal).
	 */
    private static final ResultsTable perimeterDensity_D4(ImageProcessor image, 
    		double[] resol) {

        // Create data table
        ResultsTable table = new ResultsTable();
        double perim;
                                     
        double d1  = resol[0];
        double d2  = resol[1];
        double d12 = Math.hypot(d1, d2);

        // area of a pixel (used for computing line densities)
        double pixelArea = d1 * d2;

    	// compute weights associated to each direction
    	double[] weights = computeDirectionWeightsD4(resol);

    	double area = particleArea(image, 255) * pixelArea;

    	// Count number of transitions in each direction
    	int n1 = countTransitionsD00(image, 255, false); 
    	int n2 = countTransitionsD90(image, 255, false);
    	int n3 = countTransitionsD45(image, 255, false); 
    	int n4 = countTransitionsD135(image, 255, false); 

    	// Compute weighted diameters and multiplies by associated
    	// direction weights 
    	double wd1 = (n1 / d1)  * weights[0];
    	double wd2 = (n2 / d2)  * weights[1];
    	double wd3 = (n3 / d12) * weights[2];
    	double wd4 = (n4 / d12) * weights[3];

    	// Compute perimeter
    	perim = (wd1 + wd2 + wd3 + wd4) * pixelArea * Math.PI / 2;

    	// Add new row in table
    	table.incrementCounter();

    	// area
    	table.addValue("Area", area);

    	// Compute porosity by dividing by observed area
    	int pixelCount = image.getWidth() * image.getHeight();
    	double refArea = pixelCount * pixelArea;
    	table.addValue("A. Density", area / refArea);

    	if (debug) {
    		// Display individual counts
    		table.addValue("N1", n1);
    		table.addValue("N2", n2);
    		table.addValue("N3", n3);
    		table.addValue("N4", n4);
    	}

    	// Display perimeter value
    	table.addValue("Perimeter", perim);

    	// compute perimeter density
		double refArea2 = (image.getWidth() - 1) * (image.getHeight() - 1)
				* pixelArea;
		double perimDensity = perim / refArea2;
    	table.addValue("P. Density", perimDensity);

        return table;
    }

    /**
     * Counts the number of transitions in the horizontal direction, by counting
     * +1 when the structure touches image edges.
     */
    private static final int countTransitionsD00(ImageProcessor image, int label, boolean countBorder) {
        int width 	= image.getWidth();
        int height 	= image.getHeight();
    
        int count = 0;
        int previous = 0;
        int current;
        
        // iterate on image pixels
        for (int y = 0; y < height; y++) {
        	
        	// Count border of image
        	previous = image.get(0, y);
        	if (countBorder && previous == label)
        		count++;
        	
        	// count middle of image
            for (int x = 0; x < width; x++) {
                current = image.get(x, y);
                if (previous == label ^ current == label) // Exclusive or
                	count++;
                previous = current;
            }
            
            // Count border of image
        	if (countBorder && previous == label)
        		count++;
        }

        return count;
    }
    
    
    /**
     * Counts the number of transitions in the horizontal direction, by counting
     * 1 when structure touches image edges.
     */
    private static final int countTransitionsD90(ImageProcessor image, int label, boolean countBorder) {
        int width 	= image.getWidth();
        int height 	= image.getHeight();
    
        int count = 0;
        int previous = 0;
        int current;
        
        // iterate on image pixels
        for (int x = 0; x < width; x++) {
        	
        	// Count border of image
        	previous = image.get(x, 0);
        	if (countBorder && previous == label)
        		count++;
        	
        	// count middle of image
            for (int y = 0; y < height; y++) {
                current = image.get(x, y);
                if (previous == label ^ current == label) // Exclusive or
                	count++;
                previous = current;
            }
            
        	if (countBorder && previous == label)
        		count++;
        }

        return count;
    }
    
    /**
     * Counts the number of transitions in the upper diagonal direction, by counting
     * 1 when structure touches image edges.
     */
    private static final int countTransitionsD45(ImageProcessor image, int label, boolean countBorder) {
        int width 	= image.getWidth();
        int height 	= image.getHeight();
        int nDiags 	= width + height - 1;
    
        int count = 0;
        int previous = 0;
        int current;
        
        // iterate on image pixels
        for (int i = 0; i < nDiags; i++) {
        	int x0 = Math.max(width - i - 1, 0);
        	int x1 = Math.min(nDiags - i, width);
        	int y0 = Math.max(i + 1 - width, 0);
        	int y1 = Math.min(i + 1, height);
        	
        	
        	// Count first line border
        	previous = image.get(x0, y0);
        	if (countBorder && previous == label)
        		count++;
        	
        	// number of pixels on the diagonal line
        	int lineLength = x1 - x0;
        	int lineLength2 = y1 - y0;
        	assert lineLength == lineLength2 : "Bounds must be equal (upper diagonal " + i + ")";
        	
        	// count middle of line
            for (int j = 1; j < lineLength; j++) {
                current = image.get(x0 + j, y0 + j);
                if (previous == label ^ current == label) // Exclusive or
                	count++;
                previous = current;
            }
            
            // Count last line border
        	if (countBorder && previous == label)
        		count++;
        }

        return count;
    }
    
    /**
     * Counts the number of transitions in the lower diagonal direction, by counting
     * 1 when structure touches image edges.
     */
    private static final int countTransitionsD135(ImageProcessor image, int label, boolean countBorder) {
        int width 	= image.getWidth();
        int height 	= image.getHeight();
        int nDiags 	= width + height - 1;
    
        int count = 0;
        int previous = 0;
        int current;
        
        // iterate on image pixels
        for (int i = 0; i < nDiags; i++) {
        	int x0 = Math.max(i + 1 - height, 0);
        	int x1 = Math.min(i, width - 1);
        	int y0 = Math.min(i, height - 1);
        	int y1 = Math.max(i + 1 - width, 0);
        	
        	// Count first line border
        	previous = image.get(x0, y0);
        	if (countBorder && previous == label)
        		count++;
        	
        	// number of pixels on the diagonal line
        	int lineLength = x1 - x0;
        	int lineLength2 = y0 - y1;
        	assert lineLength == lineLength2 : "Bounds must be equal (lower diagonal " + i + ")";
        	
        	// count middle of line
            for (int j = 1; j <= lineLength; j++) {
                current = image.get(x0 + j, y0 - j);
                if (previous == label ^ current == label) // Exclusive or
                	count++;
                previous = current;
            }
            
            // Count last line border
        	if (countBorder && previous == label)
        		count++;
        }

        return count;
    }
    
    /**
     * Computes a set of weights for the four main directions (orthogonal plus
     * diagonal) in discrete image. The sum of the weights equals 1.
     * 
     * @param resol an array with the resolution in x and y directions
     * @return the set of normalized weights
     */
    private static final double[] computeDirectionWeightsD4(double[] resol) {
    	
    	// resolution in each direction
    	double d1 = resol[0];
    	double d2 = resol[1];
    	
    	// angle of the diagonal
    	double theta = Math.atan2(d2, d1);

    	// angular sector for direction 1 ([1 0])
    	double alpha1 = theta / Math.PI;

    	// angular sector for direction 2 ([0 1])
    	double alpha2 = (Math.PI / 2.0 - theta)  / Math.PI;

    	// angular sector for directions 3 and 4 ([1 1] and [-1 1])
    	double alpha34 = .25;

    	// concatenate the different weights
    	return new double[]{alpha1, alpha2, alpha34, alpha34};
    }
    
    /**
     * Computes inertia ellipse of each region in input label image.
     */
    public final static ResultsTable inertiaEllipse(ImageProcessor image) {
        // Check validity of parameters
        if (image==null) return null;
        
        // size of image
        int width = image.getWidth();
        int height = image.getHeight();
        
        // extract particle labels
        int[] labels = LabelImages.findAllLabels(image);
        int nLabels = labels.length;

        // create associative array to know index of each label
        HashMap<Integer, Integer> labelIndices = new HashMap<Integer, Integer>();
        for (int i = 0; i < nLabels; i++) {
        	labelIndices.put(labels[i], i);
        }
        
        // allocate memory for result
        int[] counts = new int[nLabels];
        double[] cx = new double[nLabels];
        double[] cy = new double[nLabels];
        double[] Ixx = new double[nLabels];
        double[] Iyy = new double[nLabels];
        double[] Ixy = new double[nLabels];

        // compute centroid of each region
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
            	int label = image.get(x, y);
            	if (label == 0)
            		continue;
            	
            	int index = labelIndices.get(label);
            	cx[index] += x;
            	cy[index] += y;
            	counts[index]++;
            }
        }
    	
        // normalize by number of pixels in each region
        for (int i = 0; i < nLabels; i++) {
        	cx[i] = cx[i] / counts[i];
        	cy[i] = cy[i] / counts[i];
        }

        // compute centered inertia matrix of each label
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
            	int label = image.get(x, y);
            	if (label == 0)
            		continue;
            	
            	int index = labelIndices.get(label);
            	double x2 = x - cx[index];
            	double y2 = y - cy[index];
            	Ixx[index] += x2 * x2;
            	Ixy[index] += x2 * y2;
            	Iyy[index] += y2 * y2;
            }
        }
    	
        // normalize by number of pixels in each region 
        for (int i = 0; i < nLabels; i++) {
        	Ixx[i] = Ixx[i] / counts[i] + 1./12.;
        	Ixy[i] = Ixy[i] / counts[i];
        	Iyy[i] = Iyy[i] / counts[i] + 1./12.;
        }

        // Create data table
        ResultsTable table = new ResultsTable();
        
        // compute ellipse parameters for each region
        final double sqrt2 = sqrt(2);
        for (int i = 0; i < nLabels; i++) {
        	double xx = Ixx[i];
        	double xy = Ixy[i];
        	double yy = Iyy[i];

            // compute ellipse semi-axes lengths
        	double common = sqrt((xx - yy) * (xx - yy) + 4 * xy * xy);
        	double ra = sqrt2 * sqrt(xx + yy + common);
        	double rb = sqrt2 * sqrt(xx + yy - common);

        	// compute ellipse angle and convert into degrees
            double theta = Math.toDegrees(Math.atan2(2 * xy, xx - yy) / 2);
            
            table.incrementCounter();
            table.addLabel(Integer.toString(labels[i]));
            // add coordinates of origin pixel (IJ coordinate system) 
            table.addValue("XCentroid", cx[i] + .5);
        	table.addValue("YCentroid", cy[i] + .5);
        	table.addValue("Radius1", ra);
        	table.addValue("Radius2", rb);
        	table.addValue("Orientation", theta);
        }

        return table;
    }
    
    
    // ====================================================
    // Utility functions 

//    /**
//     * Computes all the unique labels existing in the image, excluding label 0
//     * (used for background). The result is sorted in ascend order.
//     */
//    private static int[] findAllLabels(ImageProcessor image) {
//        int width 	= image.getWidth();
//        int height 	= image.getHeight();
//        
//        TreeSet<Integer> labels = new TreeSet<Integer> ();
//        
//        // iterate on image pixels
//        for (int y = 0; y < height; y++) 
//            for (int x = 0; x < width; x++) 
//                labels.add(image.get(x, y));
//        
//        // remove 0 if it exists
//        if (labels.contains(0))
//            labels.remove(0);
//        
//        // convert to an array of integers
//        int[] array = new int[labels.size()];
//        Iterator<Integer> iterator = labels.iterator();
//        for (int i = 0; i < labels.size(); i++) 
//            array[i] = iterator.next();
//        
//        return array;
//    }

}
