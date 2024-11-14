package com.kavach.net

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kavach.net.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val cameraActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedText = result.data?.getStringExtra(CameraActivity.EXTRA_SCANNED_TEXT)
            if (!scannedText.isNullOrEmpty()) {
                binding.messageInput.setText(scannedText)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.cameraButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                launchCamera()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.decryptButton.setOnClickListener {
            val message = binding.messageInput.text.toString()
            val shiftStr = binding.shiftInput.text.toString()

            if (message.isEmpty()) {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (shiftStr.isEmpty()) {
                Toast.makeText(this, "Please enter a shift value", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val shift = shiftStr.toIntOrNull()
            if (shift == null || shift !in 0..25) {
                Toast.makeText(this, "Shift value must be between 0 and 25", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val decrypted = decryptMessage(message, shift)
            displayResults(decrypted)
        }

        binding.autoDecryptButton.setOnClickListener {
            val message = binding.messageInput.text.toString()
            if (message.isEmpty()) {
                Toast.makeText(this, "Please enter a message to decrypt", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            autoDecrypt(message)
        }
    }

    private fun decryptMessage(message: String, shift: Int): String {
        return message.map { char ->
            when {
                char.isUpperCase() -> {
                    val shifted = (char.code - 'A'.code - shift + 26) % 26
                    (shifted + 'A'.code).toChar()
                }
                char.isLowerCase() -> {
                    val shifted = (char.code - 'a'.code - shift + 26) % 26
                    (shifted + 'a'.code).toChar()
                }
                else -> char
            }
        }.joinToString("")
    }

    private fun launchCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        cameraActivityLauncher.launch(intent)
    }

    private val commonEnglishWords = setOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on", "with", 
        "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we", "say", "her", 
        "she", "or", "an", "will", "my", "one", "all", "would", "there", "their", "what", "so", "up", 
        "out", "if", "about", "who", "get", "which", "go", "me", "when", "make", "can", "like", "time", 
        "no", "just", "him", "know", "take", "people", "into", "year", "your", "good", "some", "could", 
        "them", "see", "other", "than", "then", "now", "look", "only", "come", "its", "over", "think", 
        "also", "back", "after", "use", "two", "how", "our", "work", "first", "well", "way", "even", 
        "new", "want", "because", "any", "these", "give", "day", "most", "us"
    )

    private fun autoDecrypt(message: String) {
        lifecycleScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) {
                binding.resultText.text = "🔍 Initiating brute-force decryption..."
                binding.analysisText.text = "⚡ Testing all possible combinations..."
                binding.autoDecryptButton.isEnabled = false
                binding.decryptButton.isEnabled = false
            }

            // Try all possible shifts (0-25)
            val results = (0..25).map { shift ->
                val decrypted = decryptMessage(message, shift)
                val score = calculateAdvancedReadabilityScore(decrypted)
                Triple(decrypted, score, shift)
            }

            // Sort by score in descending order
            val sortedResults = results.sortedByDescending { it.second }
            val bestResults = sortedResults.take(5) // Keep top 5 results for analysis
            val bestResult = bestResults.first()
            
            withContext(Dispatchers.Main) {
                displayResults(bestResult.first)
                binding.shiftInput.setText(bestResult.third.toString())
                binding.autoDecryptButton.isEnabled = true
                binding.decryptButton.isEnabled = true
                
                // Build detailed analysis
                val analysis = buildString {
                    append("🎯 Decryption Analysis:\n\n")
                    
                    // Show confidence and details for best result
                    val confidence = when {
                        bestResult.second > 50 -> "Very High (>90%)"
                        bestResult.second > 30 -> "High (~75%)"
                        bestResult.second > 20 -> "Medium (~50%)"
                        bestResult.second > 10 -> "Low (~25%)"
                        else -> "Very Low (<10%)"
                    }
                    
                    append("Best Match (Shift: ${bestResult.third})\n")
                    append("Confidence: $confidence\n")
                    append("Score: ${bestResult.second}\n")
                    append("Detected Words: ${countDetectedWords(bestResult.first)}\n\n")
                    
                    // Show alternative possibilities
                    append("🔄 Alternative Possibilities:\n")
                    bestResults.drop(1).forEachIndexed { index, (text, score, shift) ->
                        append("\n${index + 1}. Shift: $shift (Score: $score)\n")
                        append("Text: $text\n")
                        append("Detected Words: ${countDetectedWords(text)}\n")
                    }
                    
                    // Add pattern analysis
                    append("\n📊 Pattern Analysis:\n")
                    val patterns = analyzePatterns(bestResult.first)
                    patterns.forEach { (pattern, count) ->
                        append("$pattern: $count\n")
                    }
                }
                
                binding.analysisText.text = analysis
            }
        }
    }

    private fun calculateAdvancedReadabilityScore(text: String): Int {
        val words = text.lowercase()
            .replace(Regex("[^a-z\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        var score = 0
        
        // Common words detection (weighted heavily)
        val commonWordCount = words.count { it in commonEnglishWords }
        score += commonWordCount * 3
        
        // Word length distribution
        val normalWordLengths = words.count { it.length in 3..12 }
        score += normalWordLengths
        
        // Consecutive words check
        for (i in 0 until words.size - 1) {
            if (words[i] in commonEnglishWords && words[i + 1] in commonEnglishWords) {
                score += 3 // Bonus for meaningful phrases
            }
        }
        
        // Letter frequency analysis (English language)
        val letterFrequencies = mapOf(
            'e' to 12.7, 't' to 9.1, 'a' to 8.2, 'o' to 7.5, 'i' to 7.0,
            'n' to 6.7, 's' to 6.3, 'h' to 6.1, 'r' to 6.0, 'd' to 4.3
        )
        
        val textLetters = text.lowercase().filter { it.isLetter() }
        val totalLetters = textLetters.length.toDouble()
        
        if (totalLetters > 0) {
            letterFrequencies.forEach { (letter, expectedFreq) ->
                val actualFreq = (textLetters.count { it == letter } / totalLetters) * 100
                val freqScore = 10 - Math.abs(expectedFreq - actualFreq)
                if (freqScore > 0) score += freqScore.toInt()
            }
        }
        
        // Penalize suspicious patterns
        score -= words.count { word ->
            word.length > 2 && word.toSet().size == word.length && // All different letters
            word.none { it in "aeiou" } // No vowels
        } * 2
        
        // Bonus for proper word spacing
        score += text.count { it == ' ' } / 5
        
        return score
    }

    private fun countDetectedWords(text: String): String {
        val words = text.lowercase()
            .replace(Regex("[^a-z\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        
        val common = words.count { it in commonEnglishWords }
        val total = words.size
        
        return "$common/$total words recognized"
    }

    private fun analyzePatterns(text: String): Map<String, Int> {
        val patterns = mutableMapOf<String, Int>()
        
        // Count vowels
        patterns["Vowels"] = text.count { it.lowercaseChar() in "aeiou" }
        
        // Count consonants
        patterns["Consonants"] = text.count { it.isLetter() && it.lowercaseChar() !in "aeiou" }
        
        // Count words
        patterns["Words"] = text.split(Regex("\\s+")).size
        
        // Count capital letters
        patterns["Capitals"] = text.count { it.isUpperCase() }
        
        // Count punctuation
        patterns["Punctuation"] = text.count { it in ".,!?;:" }
        
        // Count numbers
        patterns["Numbers"] = text.count { it.isDigit() }
        
        return patterns
    }

    private fun displayResults(decryptedText: String) {
        binding.resultText.text = decryptedText
        analyzeText(decryptedText)
    }

    private fun analyzeText(text: String) {
        val keywords = extractKeywords(text)
        val analysis = buildAnalysis(keywords)
        binding.analysisText.text = analysis
    }

    private fun extractKeywords(text: String): Map<String, List<String>> {
        val dates = Regex("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b").findAll(text).map { it.value }.toList()
        val times = Regex("\\b\\d{1,2}:\\d{2}\\b").findAll(text).map { it.value }.toList()
        val locations = Regex("\\b(north|south|east|west|building|street|road|avenue|place)\\b", 
            RegexOption.IGNORE_CASE).findAll(text).map { it.value }.toList()
        val actions = Regex("\\b(attack|defend|move|secure|breach|hack|encrypt|decrypt|access|steal|obtain)\\b",
            RegexOption.IGNORE_CASE).findAll(text).map { it.value }.toList()

        return mapOf(
            "Dates" to dates,
            "Times" to times,
            "Locations" to locations,
            "Actions" to actions
        )
    }

    private fun buildAnalysis(keywords: Map<String, List<String>>): String {
        val analysis = StringBuilder()
        
        keywords.forEach { (category, words) ->
            if (words.isNotEmpty()) {
                analysis.append("$category found: ${words.joinToString(", ")}\n")
            }
        }

        if (analysis.isEmpty()) {
            analysis.append("No significant patterns or keywords found in the decrypted text.")
        }

        return analysis.toString()
    }
}
