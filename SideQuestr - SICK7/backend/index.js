const admin = require('firebase-admin');
const { GoogleGenerativeAI } = require("@google/generative-ai");

const serviceAccountKey = require('./serviceAccountKey.json');

// 1. Initialize Firebase Admin
admin.initializeApp({
    credential: admin.credential.cert(serviceAccountKey)
});
const db = admin.firestore();

// 2. Initialize the AI API
const genAI = new GoogleGenerativeAI(""); // <-- Put your key here

// Helper functions
const normalizeStr = (str) => str.toLowerCase().replace(/[^a-z0-9]/g, '');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

async function generateAndSaveQuests() {
    try {
        console.log("Fetching existing quests from database...");
        const snapshot = await db.collection('quests').get();
        const existingQuests = snapshot.docs.map(doc => doc.data());
        const existingNames = existingQuests.map(q => q.name);

        let exclusionRule = "";
        if (existingNames.length > 0) {
            exclusionRule = `CRITICAL RULE: DO NOT use any of these existing locations: ${existingNames.join(', ')}.`;
        }

        const model = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });

        const prompt = `
            Generate exactly 5 different, fun, gamified side-quests set at POPULAR, well-known landmarks or tourist attractions in Bucharest, Romania. 
            
            ${exclusionRule}
            
            For each quest, the user needs to take a picture of themselves at the location. 
            Return ONLY a raw JSON ARRAY of objects. Each object must have these exact keys: 
            "name" (string), "details" (string), "latitude" (float), "longitude" (float).
            Do not include markdown formatting like \`\`\`json.
            
            Example format:
            [
              {"name": "Vampire's Arch", "details": "Take a selfie making a scary face in front of the Arcul de Triumf.", "latitude": 44.4671, "longitude": 26.0782},
              {"name": "Another Place", "details": "...", "latitude": 44.1111, "longitude": 26.1111}
            ]
        `;

        let aiResponse = "";
        let successfullyFetched = false;

        // --- NEW: The Auto-Pause Loop is back! ---
        while (!successfullyFetched) {
            try {
                console.log("Asking AI for a batch of 5 new quests. Please wait...");
                const result = await model.generateContent(prompt);
                aiResponse = result.response.text();
                successfullyFetched = true; // If we get here, it worked!

            } catch (apiError) {
                const isRateLimit = apiError.status === 429 ||
                    apiError.status === '429' ||
                    (apiError.message && apiError.message.includes('429'));

                if (isRateLimit) {
                    console.log("⏳ Google's free-tier limit hit. Pausing for 60 seconds to let it reset...");
                    await delay(60000); // Wait 1 minute and the while-loop will try again
                } else {
                    throw apiError; // Crash if it's a real error (like a wrong password)
                }
            }
        }

        // Clean up markdown tags
        aiResponse = aiResponse.replace(/```json/gi, '').replace(/```/g, '').trim();

        // Parse the AI's JSON array
        const newQuestsArray = JSON.parse(aiResponse);
        let addedCount = 0;

        console.log("Checking for duplicates...");

        // Loop through the array the AI gave us and check them against the database
        for (const newQuest of newQuestsArray) {
            const newNameNormalized = normalizeStr(newQuest.name);

            // Fuzzy Name Check
            const isNameDuplicate = existingQuests.some(q => {
                const existingNorm = normalizeStr(q.name);
                return existingNorm === newNameNormalized ||
                    existingNorm.includes(newNameNormalized) ||
                    newNameNormalized.includes(existingNorm);
            });

            // GPS Proximity Check
            const isLocationDuplicate = existingQuests.some(q => {
                const latDiff = Math.abs(q.latitude - newQuest.latitude);
                const lngDiff = Math.abs(q.longitude - newQuest.longitude);
                return latDiff < 0.002 && lngDiff < 0.002;
            });

            if (isNameDuplicate || isLocationDuplicate) {
                console.log(`⚠️ Skipped duplicate: ${newQuest.name}`);
            } else {
                // It is unique! Add it to Firebase
                await db.collection('quests').add(newQuest);
                console.log(`✅ Success! Added: ${newQuest.name}`);

                existingQuests.push(newQuest);
                addedCount++;
            }
        }

        console.log(`\n🎉 Finished! Added ${addedCount} new unique quests to your database.`);

    } catch (error) {
        console.error("❌ Failed to generate quests:", error);
    }
}

// Run the function
generateAndSaveQuests();