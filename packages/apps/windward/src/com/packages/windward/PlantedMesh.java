package com.packages.windward;

import java.util.ArrayList;
import java.util.List;

/** Static ground cover, baked against the same triangular surface used by walking. */
final class PlantedMesh {
    final List<float[]> parts=new ArrayList<>();
    double radius;
    static boolean supports(String name){
        return name.equals("grass")||name.equals("flowers")||name.equals("fern")||name.equals("mushrooms");
    }
    PlantedMesh(MountainAssets.Model model,double x,double base,double z,double height,double angle,double horizontalRadius){
        double scale=height/model.height(),horizontal=scale;
        double cx=(model.minX+model.maxX)/2,cz=(model.minZ+model.maxZ)/2;
        if(horizontalRadius>0){
            double extent=0;
            for(MountainAssets.Part part:model.parts)for(int i=0;i<part.vertices.length;i+=8)
                extent=Math.max(extent,Math.hypot(part.vertices[i]-cx,part.vertices[i+2]-cz));
            horizontal=horizontalRadius/extent;
        }
        double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));
        MountainPlanet.Frame frame=MountainPlanet.frame(x,z);
        for(MountainAssets.Part part:model.parts){
            float[] v=part.vertices.clone();
            for(int i=0;i<v.length;i+=8){
                double px=(v[i]-cx)*horizontal,pz=(v[i+2]-cz)*horizontal;
                double dx=c*px+s*pz,dz=-s*px+c*pz;
                MountainPlanet.Vec direction=frame.point(dx,0,dz).unit();
                double lon=MountainPlanet.longitude(direction),lat=MountainPlanet.latitude(direction);
                // Slight burial hides the seam; height above the root plane is preserved.
                double altitude=PlanetTerrain.ground(lon,lat)+(v[i+1]-model.minY)*scale-.015;
                MountainPlanet.Vec local=direction.mul(MountainPlanet.RADIUS+altitude).sub(frame.origin);
                double lx=local.dot(frame.east),ly=local.dot(frame.up),lz=local.dot(frame.south);
                v[i]=(float)(x+lx);v[i+1]=(float)ly;v[i+2]=(float)(z+lz);
                // Inverse transpose of the original nonuniform scale, followed by yaw.
                double nx=v[i+3]/horizontal,ny=v[i+4]/scale,nz=v[i+5]/horizontal;
                double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
                if(length>0){v[i+3]=(float)((c*nx+s*nz)/length);v[i+4]=(float)(ny/length);v[i+5]=(float)((-s*nx+c*nz)/length);}
                radius=Math.max(radius,Math.sqrt(lx*lx+(ly-base-height/2)*(ly-base-height/2)+lz*lz));
            }
            parts.add(v);
        }
        radius+=.05;
    }
}
