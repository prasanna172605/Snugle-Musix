package com.snuggle.music.personalization

enum class MusicLanguage(val displayName: String, val searchKeywords: List<String>) {
    TAMIL("Tamil", listOf("tamil", "kollywood", "anirudh", "ar rahman", "ilayaraja", "yuvan", "harris jayaraj", "santhosh narayanan", "dharan", "d imman", "gv prakash", "sidsriram", "pradeep kumar", "jonita", "shreya ghoshal")),
    MALAYALAM("Malayalam", listOf("malayalam", "mollywood", "sushin shyam", "heshem abdul wahab", "shaan rahman", "jakes bejoy", "gopi sundar", "ks harisankar", "vidyasagar")),
    TELUGU("Telugu", listOf("telugu", "tollywood", "thaman", "dsp", "devi sri prasad", "keeravani", "anurag kulkarni", "sid sriram", "ram miriyala", "mickey j meyer")),
    KANNADA("Kannada", listOf("kannada", "sandalwood", "ravi basrur", "charan raj", "b ajaneesh loknath", "arjun janya", "vasuki vaibhav", "raghu dixit")),
    HINDI("Hindi", listOf("hindi", "bollywood", "arijit singh", "pritam", "badshah", "neha kakkar", "vishal mishra", "jubin nautiyal", "sachet tandon", "shreya ghoshal", "atif aslam", "armaan malik", "sachin jigar")),
    PUNJABI("Punjabi", listOf("punjabi", "diljit dosanjh", "karan aujla", "shubh", "sidhu moose wala", "ap dhillon", "gurdas maan", "amrit maan", "jassi gill", "b praak", "jaani")),
    MARATHI("Marathi", listOf("marathi", "ajay atul", "avdhoot gupte", "swapnil bandodkar", "adarsh shinde")),
    BENGALI("Bengali", listOf("bengali", "anupam roy", "arijit singh", "shreya ghoshal", "somlata", "jeep ganguly")),
    GUJARATI("Gujarati", listOf("gujarati", "garba", "kinjal dave", "geeta rabari", "jignesh kaviraj")),
    ENGLISH("English", listOf("english", "pop", "hip hop", "rock", "taylor swift", "the weeknd", "drake", "ed sheeran", "billie eilish", "dua lipa", "post malone", "ariana grande", "bruno mars", "eminem", "coldplay", "imagine dragons")),
    GLOBAL("Global", emptyList());

    companion object {
        fun fromNameOrNull(name: String?): MusicLanguage? {
            if (name.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) || it.displayName.equals(name, ignoreCase = true) }
        }
    }
}
