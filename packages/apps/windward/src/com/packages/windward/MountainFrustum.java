package com.packages.windward;

/** Conservative sphere test using the same perspective and look-at basis as the renderer. */
final class MountainFrustum {
    private double ex,ey,ez,fx,fy,fz,sx,sz,ux,uy,uz;
    private static final double V=Math.tan(Math.toRadians(65)/2),H=V*1.5;
    private static final double VN=Math.sqrt(1+V*V),HN=Math.sqrt(1+H*H);
    void update(MountainWorld w,MountainCamera c){
        ex=c.eyeX;ey=c.eyeY;ez=c.eyeZ;fx=w.x-ex;fy=w.y+1.35-ey;fz=w.z-ez;
        double length=Math.sqrt(fx*fx+fy*fy+fz*fz);fx/=length;fy/=length;fz/=length;
        length=Math.hypot(fx,fz);sx=-fz/length;sz=fx/length;
        ux=-sz*fy;uy=sz*fx-sx*fz;uz=sx*fy;
    }
    boolean visible(double x,double y,double z,double radius){
        double dx=x-ex,dy=y-ey,dz=z-ez,depth=dx*fx+dy*fy+dz*fz;
        if(depth+radius<.1||depth-radius>220)return false;
        return Math.abs(dx*sx+dz*sz)<=depth*H+radius*HN&&Math.abs(dx*ux+dy*uy+dz*uz)<=depth*V+radius*VN;
    }
}
