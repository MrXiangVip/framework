package com.packages.windward;

/** Conservative sphere test using the same perspective and look-at basis as the renderer. */
final class MountainFrustum {
    private double far=220;
    private double ex,ey,ez,fx,fy,fz,sx,sy,sz,ux,uy,uz;
    private static final double V=Math.tan(Math.toRadians(65)/2),H=V*1.5;
    private static final double VN=Math.sqrt(1+V*V),HN=Math.sqrt(1+H*H);
    void update(MountainWorld w,MountainCamera c){
        MountainPlanet.Frame frame=MountainPlanet.frame(w.x,w.z);
        MountainPlanet.Vec eye=frame.point(c.eyeX-w.x,c.eyeY,c.eyeZ-w.z),target=frame.point(0,c.targetAltitude(w),0);
        far=c.farClip();
        ex=eye.x;ey=eye.y;ez=eye.z;
        MountainPlanet.Vec forward=target.sub(eye).unit(),side=forward.cross(frame.up).unit(),up=side.cross(forward);
        fx=forward.x;fy=forward.y;fz=forward.z;sx=side.x;sy=side.y;sz=side.z;ux=up.x;uy=up.y;uz=up.z;
    }
    boolean visible(double x,double y,double z,double radius){
        double dx=x-ex,dy=y-ey,dz=z-ez,depth=dx*fx+dy*fy+dz*fz;
        if(depth+radius<.1||depth-radius>far)return false;
        return Math.abs(dx*sx+dy*sy+dz*sz)<=depth*H+radius*HN&&Math.abs(dx*ux+dy*uy+dz*uz)<=depth*V+radius*VN;
    }
}
