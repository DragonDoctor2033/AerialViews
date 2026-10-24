package com.neilturner.aerialviews.testutils

/**
 * Answers of the OpenWeather current weather API, as the API sends them, with a lot more in them
 * than the app reads. What the weather effects read (rain, snow, clouds, sys) is left out by the
 * API when there is none of it.
 */
internal object OpenWeatherSamples {
    /** The example of https://openweathermap.org/current: moderate rain in Zocca, at 14:03 UTC, in the day */
    val RAIN =
        """
        {
          "coord": { "lon": 10.99, "lat": 44.34 },
          "weather": [
            { "id": 501, "main": "Rain", "description": "moderate rain", "icon": "10d" }
          ],
          "base": "stations",
          "main": {
            "temp": 298.48,
            "feels_like": 298.74,
            "temp_min": 297.56,
            "temp_max": 300.05,
            "pressure": 1015,
            "humidity": 64,
            "sea_level": 1015,
            "grnd_level": 933
          },
          "visibility": 10000,
          "wind": { "speed": 0.62, "deg": 349, "gust": 1.18 },
          "rain": { "1h": 3.16 },
          "clouds": { "all": 100 },
          "dt": 1661870592,
          "sys": {
            "type": 2,
            "id": 2075663,
            "country": "IT",
            "sunrise": 1661834187,
            "sunset": 1661882248
          },
          "timezone": 7200,
          "id": 3163858,
          "name": "Zocca",
          "cod": 200
        }
        """.trimIndent()

    /** Snow in London at 23:44 UTC on a January night, after sunset */
    val SNOW =
        """
        {
          "coord": { "lon": -0.1257, "lat": 51.5085 },
          "weather": [
            { "id": 601, "main": "Snow", "description": "snow", "icon": "13n" }
          ],
          "base": "stations",
          "main": {
            "temp": 272.15,
            "feels_like": 267.8,
            "temp_min": 271.3,
            "temp_max": 273.0,
            "pressure": 1008,
            "humidity": 92
          },
          "visibility": 2800,
          "wind": { "speed": 4.12, "deg": 70, "gust": 8.2 },
          "snow": { "1h": 0.89 },
          "clouds": { "all": 100 },
          "dt": 1675123456,
          "sys": { "type": 2, "id": 2075535, "country": "GB", "sunrise": 1675065600, "sunset": 1675097100 },
          "timezone": 0,
          "id": 2643743,
          "name": "London",
          "cod": 200
        }
        """.trimIndent()

    /** What the app has always asked for and parsed: no rain, snow, clouds or sys at all */
    val BARE =
        """
        {
          "weather": [ { "id": 800, "main": "Clear", "description": "clear sky", "icon": "01d" } ],
          "main": { "temp": 12.6, "feels_like": 11.2, "temp_min": 10.0, "temp_max": 14.0, "pressure": 1012, "humidity": 77 },
          "wind": { "speed": 9.8, "deg": 180 },
          "name": "Dublin"
        }
        """.trimIndent()
}
