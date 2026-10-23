package meblox;

public final class TimeTrialLevel extends GameLevel {
    public int id(){return 7;}
    public String name(){return "Carrera contrarreloj";}
    public float[][] blocks(){return new ObbyLevel().blocks();}
}
