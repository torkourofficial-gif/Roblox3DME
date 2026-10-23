package meblox;

/** Registry of the game's individually maintained level definitions. */
public final class LevelCatalog {
    private LevelCatalog() {}
    public static String[] names() {
        return new String[] {"Obby","Baseplate","Horda zombi","Studio","Lava ascendente",
            "Lluvia de meteoritos","Plataformas que caen","Carrera contrarreloj",
            "Caza de monedas","Rey de la colina","Arena de espadas","Escondite",
            "Simon dice","Mini tycoon","Defensa de base"};
    }
    public static GameLevel get(int id) {
        switch(id) {
            case 0:return new ObbyLevel();
            case 1:return new BaseplateLevel();
            case 2:return new ZombieHordeLevel();
            case 3:return new StudioLevel();
            case 4:return new LavaRiseLevel();
            case 5:return new MeteorShowerLevel();
            case 6:return new FallingTilesLevel();
            case 7:return new TimeTrialLevel();
            case 8:return new CoinHuntLevel();
            case 9:return new KingHillLevel();
            case 10:return new SwordArenaLevel();
            case 11:return new HideSeekLevel();
            case 12:return new SimonSaysLevel();
            case 13:return new MiniTycoonLevel();
            default:return new BaseDefenseLevel();
        }
    }
}
