package com.CraftPlay777.jps;

/** Un lugar en el mapa: nombre (puede ser null mientras se busca) y coordenadas. */
final class Place {
    String name;
    final double lat;
    final double lon;

    // Datos extra de OpenStreetMap (solo en resultados de búsqueda); sirven para dibujar el contorno.
    String osmType;   // "node", "way" o "relation"
    long osmId;
    double[] bbox;    // {sur, norte, oeste, este}

    Place(String name, double lat, double lon) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }
}
