package volvis;

import gui.TransferFunction2DEditor;
import java.awt.image.BufferedImage;
import util.VectorMath;
import volume.GradientVolume;
import volume.Volume;

/** *
 * @author Stan Roelofs
 */
public abstract class Raycaster {
    
    protected int imageCenter;
    protected int delta;
    protected BufferedImage image;
    protected double[] viewVec;
    protected double[] uVec;
    protected double[] vVec;
    protected double max;
    protected TFColor voxelColor;
    protected double[] pixelCoord;
    protected double[] volumeCenter;
    protected Volume volume;
    protected TransferFunction tFunc;
    protected TransferFunction2DEditor tfEditor2D;
    protected GradientVolume gradients;
    protected boolean phong;
        
    public Raycaster(int delta) {
        this.delta = delta;
    }
    
    // This function assumes all parameters are equal for all color components
    protected TFColor phong(double[] coord, TFColor color) {    
        if (coord[0] < 1 || coord[0] >= volume.getDimX()-1 || coord[1] < 1 || coord[1] >= volume.getDimY()-1
                || coord[2] < 1 || coord[2] >= volume.getDimZ()-1) {
            return color;
        }
        
        // Define parameters
        double k_ambient = 0.1;
        double k_diff = 0.7;
        double k_spec = 0.2;
        double alpha = 10;
        
        // Color of the light source
        TFColor lightColor = new TFColor(color.r,color.g,color.b,1);
        
        // Calculate l_a * k_ambient
        double part1_r = lightColor.r * k_ambient;
        double part1_g = lightColor.g * k_ambient;
        double part1_b = lightColor.b * k_ambient;
        
        // Calculate l_l
        double k1k2d = 0.8; // Should actually be k1*k2*d(x)
        double l_l_r = lightColor.r / k1k2d;
        double l_l_g = lightColor.g / k1k2d;
        double l_l_b = lightColor.b / k1k2d;       
        
        // Get normalized local gradient vector        
        // Not sure using math.floor is the correct way of doing this ...
        int x = (int) Math.floor(coord[0]);
        int y = (int) Math.floor(coord[1]);
        int z = (int) Math.floor(coord[2]);
        double gx = (0.5*(volume.getVoxel(x+1,y,z)-volume.getVoxel(x-1,y,z)));
        double gy = (0.5*(volume.getVoxel(x,y+1,z)-volume.getVoxel(x,y-1,z)));
        double gz = (0.5*(volume.getVoxel(x,y,z+1)-volume.getVoxel(x,y,z-1)));
        double[] s = {gx, gy, gz};
        //double mag = getInterpolatedGradient(coord);
        
        // Calculate normalized gradient vector
        double[] N = VectorMath.normalize(s);
        
        // Since all vectors have viewpoint as origin, L is simply opposite of coord (right ...?)
        double[] L = {-1*viewVec[0], -1*viewVec[1], -1*viewVec[2]};
                
        // Calculate L dotproduct N
        double NL = VectorMath.dotproduct(L,N);
        double part2 = k_diff * NL;
        
        // Calculate H, since light comes from view, V = L
        double[] V = L;
        double[] VplusL = VectorMath.add(V, L);
        double[] H = VectorMath.normalize(VplusL);
        
        // Calculate NH
        double NH = VectorMath.dotproduct(N,H);
        
        double part3 = k_spec * Math.pow(NH, alpha);
        
        TFColor result = new TFColor(part1_r, part1_g, part1_b, color.a);
        
        if (NL > 0) {
            result.r += l_l_r * part2;
            result.g += l_l_g * part2;
            result.b += l_l_b * part2;
        }
        
        if (NH > 0) {
            result.r += l_l_r * part3;
            result.g += l_l_g * part3;
            result.b += l_l_b * part3;
        }
        
        return result;
    }
    
    protected short getVoxel(double[] coord) {
        if (coord[0] < 0 || coord[0] >= volume.getDimX() || coord[1] < 0 || coord[1] >= volume.getDimY()
                || coord[2] < 0 || coord[2] >= volume.getDimZ()) {
            return 0;
        }

        int x = (int) Math.floor(coord[0]);
        int y = (int) Math.floor(coord[1]);
        int z = (int) Math.floor(coord[2]);

        return volume.getVoxel(x, y, z);
    }   
    
    protected short getInterpolatedVoxel(double[] coord) {
        if (coord[0] < 0 || coord[0] >= volume.getDimX() || coord[1] < 0 || coord[1] >= volume.getDimY()
                || coord[2] < 0 || coord[2] >= volume.getDimZ()) {
            return 0;
        }
        
        double x = coord[0];
        double y = coord[1];
        double z = coord[2];
        
        int xFloor = (int) Math.floor(x);
        int yFloor = (int) Math.floor(y);
        int zFloor = (int) Math.floor(z);
        int xCeil = (int) Math.ceil(x);
        int yCeil = (int) Math.ceil(y);
        int zCeil = (int) Math.ceil(z);   
        
        if (xCeil >= volume.getDimX() || yCeil >= volume.getDimY() || zCeil >= volume.getDimZ()) {
            return 0;
        }
        
        short Sx0 = volume.getVoxel(xFloor, yFloor, zFloor);
        short Sx1 = volume.getVoxel(xCeil, yFloor, zFloor);
        short Sx2 = volume.getVoxel(xFloor, yCeil, zFloor);
        short Sx3 = volume.getVoxel(xCeil, yCeil, zFloor);       
        short Sx4 = volume.getVoxel(xFloor, yFloor, zCeil);
        short Sx5 = volume.getVoxel(xCeil, yFloor, zCeil);
        short Sx6 = volume.getVoxel(xFloor, yCeil, zCeil);
        short Sx7 = volume.getVoxel(xCeil, yCeil, zCeil);
        
        double alpha = (x - Math.floor(x)) / (Math.ceil(x) - Math.floor(x)); // (x - x0) / (x1 - x0)
        double beta = (y - Math.floor(y)) / (Math.ceil(y) - Math.floor(y));
        double gamma = (z - Math.floor(z)) / (Math.ceil(z) - Math.floor(z));
        
        double Sx = (1 - alpha) * (1 - beta) * (1 - gamma) * Sx0;
        Sx += alpha * (1 - beta) * (1 - gamma) * Sx1;
        Sx += (1 - alpha) * beta * (1 - gamma) * Sx2;
        Sx += alpha * beta * (1 - gamma) * Sx3;
        Sx += (1 - alpha) * (1 - beta) * gamma * Sx4;
        Sx += alpha * (1 - beta) * gamma * Sx5;
        Sx += (1 - alpha) * beta * gamma * Sx6;
        Sx += alpha * beta * gamma * Sx7;
        
        return (short) Sx;
    }    
    
    protected short getInterpolatedGradient(double[] coord) {
        if (coord[0] < 0 || coord[0] >= volume.getDimX() || coord[1] < 0 || coord[1] >= volume.getDimY()
                || coord[2] < 0 || coord[2] >= volume.getDimZ()) {
            return 0;
        }
        
        double x = coord[0];
        double y = coord[1];
        double z = coord[2];
        
        int xFloor = (int) Math.floor(x);
        int yFloor = (int) Math.floor(y);
        int zFloor = (int) Math.floor(z);
        int xCeil = (int) Math.ceil(x);
        int yCeil = (int) Math.ceil(y);
        int zCeil = (int) Math.ceil(z);   
        
        if (xCeil >= volume.getDimX() || yCeil >= volume.getDimY() || zCeil >= volume.getDimZ()) {
            return 0;
        }
        
        float Sx0 = gradients.getGradient(xFloor, yFloor, zFloor).mag;
        float Sx1 = gradients.getGradient(xCeil, yFloor, zFloor).mag;
        float Sx2 = gradients.getGradient(xFloor, yCeil, zFloor).mag;
        float Sx3 = gradients.getGradient(xCeil, yCeil, zFloor).mag;       
        float Sx4 = gradients.getGradient(xFloor, yFloor, zCeil).mag;
        float Sx5 = gradients.getGradient(xCeil, yFloor, zCeil).mag;
        float Sx6 = gradients.getGradient(xFloor, yCeil, zCeil).mag;
        float Sx7 = gradients.getGradient(xCeil, yCeil, zCeil).mag;
        
        double alpha = (x - Math.floor(x)) / (Math.ceil(x) - Math.floor(x)); // (x - x0) / (x1 - x0)
        double beta = (y - Math.floor(y)) / (Math.ceil(y) - Math.floor(y));
        double gamma = (z - Math.floor(z)) / (Math.ceil(z) - Math.floor(z));
        
        double Sx = (1 - alpha) * (1 - beta) * (1 - gamma) * Sx0;
        Sx += alpha * (1 - beta) * (1 - gamma) * Sx1;
        Sx += (1 - alpha) * beta * (1 - gamma) * Sx2;
        Sx += alpha * beta * (1 - gamma) * Sx3;
        Sx += (1 - alpha) * (1 - beta) * gamma * Sx4;
        Sx += alpha * (1 - beta) * gamma * Sx5;
        Sx += (1 - alpha) * beta * gamma * Sx6;
        Sx += alpha * beta * gamma * Sx7;
        
        return (short) Sx;
    }    
    
    public void render(double[] viewMatrix, BufferedImage image, Volume volume, GradientVolume gradients, TransferFunction tFunc, TransferFunction2DEditor tfEditor2D, boolean phong) {
        this.image = image;
        this.volume = volume;
        this.tFunc = tFunc;
        this.tfEditor2D = tfEditor2D;
        this.gradients = gradients;
        this.phong = phong;
        
        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        viewVec = new double[3];
        uVec = new double[3];
        vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        imageCenter = image.getWidth() / 2;

        pixelCoord = new double[3];
        volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        max = volume.getMaximum();
        voxelColor = new TFColor();
        
        this.method();
    }
    
    protected abstract void method();
    
    protected void setPixel(int i, int j, TFColor color) {
        // BufferedImage expects a pixel color packed as ARGB in an int
        int c_alpha = color.a <= 1.0 ? (int) Math.floor(color.a * 255) : 255;
        int c_red = color.r <= 1.0 ? (int) Math.floor(color.r * 255) : 255;
        int c_green = color.g <= 1.0 ? (int) Math.floor(color.g * 255) : 255;
        int c_blue = color.b <= 1.0 ? (int) Math.floor(color.b * 255) : 255;
        int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
        image.setRGB(i, j, pixelColor);
    }
}
