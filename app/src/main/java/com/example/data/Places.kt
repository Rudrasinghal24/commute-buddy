package com.example.data

data class DelhiPlace(
    val id: String,
    val name: String,
    val area: String,
    val category: String,
    val lat: Double,
    val lng: Double
)

// 30 real Delhi NCR places (metro stations, railway stations, malls, colleges)
object DelhiPlacesData {
    val places: List<DelhiPlace> = listOf(
        // Metro Stations (10)
        DelhiPlace("m1", "Rajiv Chowk Metro Station", "Connaught Place, New Delhi", "Metro Station", 28.6328, 77.2197),
        DelhiPlace("m2", "Kashmere Gate Metro Station", "Inter State Bus Terminus, Delhi", "Metro Station", 28.6675, 77.2282),
        DelhiPlace("m3", "Hauz Khas Metro Station", "Outer Ring Rd, South Delhi", "Metro Station", 28.5433, 77.2066),
        DelhiPlace("m4", "Botanical Garden Metro Station", "Sector 38, Noida", "Metro Station", 28.5641, 77.3343),
        DelhiPlace("m5", "Millennium City Centre Gurugram", "Sector 29, Gurugram", "Metro Station", 28.4595, 77.0726),
        DelhiPlace("m6", "Chandni Chowk Metro Station", "Old Delhi", "Metro Station", 28.6579, 77.2301),
        DelhiPlace("m7", "Dwarka Sector 21 Metro Station", "Sector 21, Dwarka", "Metro Station", 28.5522, 77.0583),
        DelhiPlace("m8", "Nehru Place Metro Station", "Nehru Place, South Delhi", "Metro Station", 28.5515, 77.2517),
        DelhiPlace("m9", "Cyber Hub Rapid Metro", "DLF Cyber City, Gurugram", "Metro Station", 28.4949, 77.0890),
        DelhiPlace("m10", "Lajpat Nagar Metro Station", "Central Market, Lajpat Nagar", "Metro Station", 28.5701, 77.2435),

        // Railway Stations (7)
        DelhiPlace("r1", "New Delhi Railway Station", "Paharganj / Ajmeri Gate, New Delhi", "Railway Station", 28.6429, 77.2191),
        DelhiPlace("r2", "Old Delhi Railway Station", "Chandni Chowk, Delhi Junction", "Railway Station", 28.6606, 77.2274),
        DelhiPlace("r3", "Hazrat Nizamuddin Railway Station", "Sarai Kale Khan, New Delhi", "Railway Station", 28.5876, 77.2508),
        DelhiPlace("r4", "Anand Vihar Terminal Railway Station", "Anand Vihar, East Delhi", "Railway Station", 28.6469, 77.3160),
        DelhiPlace("r5", "Delhi Sarai Rohilla Railway Station", "Karol Bagh, New Delhi", "Railway Station", 28.6625, 77.1869),
        DelhiPlace("r6", "Ghaziabad Junction Railway Station", "Railway Road, Ghaziabad", "Railway Station", 28.6534, 77.4272),
        DelhiPlace("r7", "Gurgaon Railway Station", "Rajendra Park, Gurugram", "Railway Station", 28.4889, 77.0113),

        // Malls & Commercial Hubs (7)
        DelhiPlace("mall1", "Select CITYWALK Mall", "Saket District Centre, New Delhi", "Mall", 28.5284, 77.2190),
        DelhiPlace("mall2", "DLF Mall of India", "Sector 18, Noida", "Mall", 28.5672, 77.3211),
        DelhiPlace("mall3", "Ambience Mall Vasant Kunj", "Nelson Mandela Marg, Vasant Kunj", "Mall", 28.5407, 77.1553),
        DelhiPlace("mall4", "Ambience Mall Gurugram", "NH-48, Sector 24, Gurugram", "Mall", 28.5042, 77.0964),
        DelhiPlace("mall5", "Pacific Mall Tagore Garden", "Najafgarh Rd, Subhash Nagar", "Mall", 28.6423, 77.1066),
        DelhiPlace("mall6", "Vegas Mall Dwarka", "Sector 14, Dwarka, New Delhi", "Mall", 28.5993, 77.0303),
        DelhiPlace("mall7", "Connaught Place Inner Circle", "Block A, Connaught Place", "Commercial Hub", 28.6315, 77.2167),

        // Colleges & Universities (6)
        DelhiPlace("c1", "IIT Delhi", "Hauz Khas, New Delhi", "College", 28.5450, 77.1926),
        DelhiPlace("c2", "Delhi Technological University (DTU)", "Shahbad Daulatpur, Rohini", "College", 28.7499, 77.1170),
        DelhiPlace("c3", "AIIMS New Delhi", "Ansari Nagar, Aurobindo Marg", "College", 28.5672, 77.2100),
        DelhiPlace("c4", "University of Delhi North Campus", "Vishwavidyalaya, Delhi", "College", 28.6880, 77.2100),
        DelhiPlace("c5", "Jawaharlal Nehru University (JNU)", "New Mehrauli Road, Munirka", "College", 28.5402, 77.1666),
        DelhiPlace("c6", "Netaji Subhas University of Technology (NSUT)", "Sector 3, Dwarka", "College", 28.6091, 77.0370)
    )

    // Match max 5 places by name or area
    fun searchPlaces(query: String, maxResults: Int = 5): List<DelhiPlace> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return places.take(maxResults)
        return places.filter { place ->
            place.name.contains(trimmed, ignoreCase = true) ||
                place.area.contains(trimmed, ignoreCase = true) ||
                place.category.contains(trimmed, ignoreCase = true)
        }.take(maxResults)
    }
}
