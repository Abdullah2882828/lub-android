package com.lub.app.github

object WorkflowTemplates {

    fun getAndroidBuildWorkflow(): Pair<String, String> {
        val content = """
name: Build Android APK

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'zulu'
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Build Debug APK
        run: ./gradlew assembleDebug --stacktrace
      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
""".trimIndent()
        return Pair(".github/workflows/build-apk.yml", content)
    }

    fun getGitHubPagesWorkflow(): Pair<String, String> {
        val content = """
name: Deploy GitHub Pages

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: "pages"
  cancel-in-progress: false

jobs:
  deploy:
    environment:
      name: github-pages
      url: ${'$'}{{ steps.deployment.outputs.page_url }}
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: 20
      - name: Install dependencies
        run: npm ci || npm install
      - name: Build project
        run: npm run build
      - name: Setup Pages
        uses: actions/configure-pages@v4
      - name: Upload artifact
        uses: actions/upload-pages-artifact@v3
        with:
          path: './dist'
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
""".trimIndent()
        return Pair(".github/workflows/pages.yml", content)
    }
}
