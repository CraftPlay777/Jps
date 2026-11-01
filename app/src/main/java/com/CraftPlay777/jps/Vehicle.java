package com.CraftPlay777.jps;

/** Vehículos para "Cómo llegar": cada uno usa un perfil de ruta y una velocidad media por defecto. */
final class Vehicle {

    final String id;
    final int nameRes;
    /** Perfil del servicio de rutas: "car", "bike" o "foot". */
    final String profile;
    final double defaultKmh;

    private Vehicle(String id, int nameRes, String profile, double defaultKmh) {
        this.id = id;
        this.nameRes = nameRes;
        this.profile = profile;
        this.defaultKmh = defaultKmh;
    }

    static final Vehicle[] ALL = {
            new Vehicle("car", R.string.vehicle_car, "car", 60),
            new Vehicle("moto", R.string.vehicle_moto, "car", 60),
            new Vehicle("truck", R.string.vehicle_truck, "car", 50),
            new Vehicle("bike", R.string.vehicle_bike, "bike", 15),
            new Vehicle("foot", R.string.vehicle_foot, "foot", 5),
    };

    static int indexOf(String id) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].id.equals(id)) {
                return i;
            }
        }
        return 0;
    }
}
