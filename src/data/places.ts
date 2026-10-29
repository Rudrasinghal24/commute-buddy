export interface DelhiPlace {
  id: string;
  name: string;
  area: string;
  category: string;
  lat: number;
  lng: number;
}

// 30 real Delhi NCR places (metro stations, railway stations, malls, colleges)
export const DELHI_PLACES: DelhiPlace[] = [
  { id: "m1", name: "Rajiv Chowk Metro Station", area: "Connaught Place, New Delhi", category: "Metro Station", lat: 28.6328, lng: 77.2197 },
  { id: "m2", name: "Kashmere Gate Metro Station", area: "Inter State Bus Terminus, Delhi", category: "Metro Station", lat: 28.6675, lng: 77.2282 },
  { id: "m3", name: "Hauz Khas Metro Station", area: "Outer Ring Rd, South Delhi", category: "Metro Station", lat: 28.5433, lng: 77.2066 },
  { id: "m4", name: "Botanical Garden Metro Station", area: "Sector 38, Noida", category: "Metro Station", lat: 28.5641, lng: 77.3343 },
  { id: "m5", name: "Millennium City Centre Gurugram", area: "Sector 29, Gurugram", category: "Metro Station", lat: 28.4595, lng: 77.0726 },
  { id: "m6", name: "Chandni Chowk Metro Station", area: "Old Delhi", category: "Metro Station", lat: 28.6579, lng: 77.2301 },
  { id: "m7", name: "Dwarka Sector 21 Metro Station", area: "Sector 21, Dwarka", category: "Metro Station", lat: 28.5522, lng: 77.0583 },
  { id: "m8", name: "Nehru Place Metro Station", area: "Nehru Place, South Delhi", category: "Metro Station", lat: 28.5515, lng: 77.2517 },
  { id: "m9", name: "Cyber Hub Rapid Metro", area: "DLF Cyber City, Gurugram", category: "Metro Station", lat: 28.4949, lng: 77.0890 },
  { id: "m10", name: "Lajpat Nagar Metro Station", area: "Central Market, Lajpat Nagar", category: "Metro Station", lat: 28.5701, lng: 77.2435 },
  { id: "r1", name: "New Delhi Railway Station", area: "Paharganj / Ajmeri Gate, New Delhi", category: "Railway Station", lat: 28.6429, lng: 77.2191 },
  { id: "r2", name: "Old Delhi Railway Station", area: "Chandni Chowk, Delhi Junction", category: "Railway Station", lat: 28.6606, lng: 77.2274 },
  { id: "r3", name: "Hazrat Nizamuddin Railway Station", area: "Sarai Kale Khan, New Delhi", category: "Railway Station", lat: 28.5876, lng: 77.2508 },
  { id: "r4", name: "Anand Vihar Terminal Railway Station", area: "Anand Vihar, East Delhi", category: "Railway Station", lat: 28.6469, lng: 77.3160 },
  { id: "r5", name: "Delhi Sarai Rohilla Railway Station", area: "Karol Bagh, New Delhi", category: "Railway Station", lat: 28.6625, lng: 77.1869 },
  { id: "r6", name: "Ghaziabad Junction Railway Station", area: "Railway Road, Ghaziabad", category: "Railway Station", lat: 28.6534, lng: 77.4272 },
  { id: "r7", name: "Gurgaon Railway Station", area: "Rajendra Park, Gurugram", category: "Railway Station", lat: 28.4889, lng: 77.0113 },
  { id: "mall1", name: "Select CITYWALK Mall", area: "Saket District Centre, New Delhi", category: "Mall", lat: 28.5284, lng: 77.2190 },
  { id: "mall2", name: "DLF Mall of India", area: "Sector 18, Noida", category: "Mall", lat: 28.5672, lng: 77.3211 },
  { id: "mall3", name: "Ambience Mall Vasant Kunj", area: "Nelson Mandela Marg, Vasant Kunj", category: "Mall", lat: 28.5407, lng: 77.1553 },
  { id: "mall4", name: "Ambience Mall Gurugram", area: "NH-48, Sector 24, Gurugram", category: "Mall", lat: 28.5042, lng: 77.0964 },
  { id: "mall5", name: "Pacific Mall Tagore Garden", area: "Najafgarh Rd, Subhash Nagar", category: "Mall", lat: 28.6423, lng: 77.1066 },
  { id: "mall6", name: "Vegas Mall Dwarka", area: "Sector 14, Dwarka, New Delhi", category: "Mall", lat: 28.5993, lng: 77.0303 },
  { id: "mall7", name: "Connaught Place Inner Circle", area: "Block A, Connaught Place", category: "Commercial Hub", lat: 28.6315, lng: 77.2167 },
  { id: "c1", name: "IIT Delhi", area: "Hauz Khas, New Delhi", category: "College", lat: 28.5450, lng: 77.1926 },
  { id: "c2", name: "Delhi Technological University (DTU)", area: "Shahbad Daulatpur, Rohini", category: "College", lat: 28.7499, lng: 77.1170 },
  { id: "c3", name: "AIIMS New Delhi", area: "Ansari Nagar, Aurobindo Marg", category: "College", lat: 28.5672, lng: 77.2100 },
  { id: "c4", name: "University of Delhi North Campus", area: "Vishwavidyalaya, Delhi", category: "College", lat: 28.6880, lng: 77.2100 },
  { id: "c5", name: "Jawaharlal Nehru University (JNU)", area: "New Mehrauli Road, Munirka", category: "College", lat: 28.5402, lng: 77.1666 },
  { id: "c6", name: "Netaji Subhas University of Technology (NSUT)", area: "Sector 3, Dwarka", category: "College", lat: 28.6091, lng: 77.0370 }
];
