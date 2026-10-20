# MEOW 🐈‍⬛

> **Make every attempt worthwhile.**
>
> A smarter way to study. A little more human, a little less complicated.

MEOW is a planned Android study companion built around one simple idea: **turn studying into a focused, connected, and meaningful experience.**

Import your question-bank PDFs, work through questions, track your performance, and revisit the mistakes that matter. MEOW aims to bring the entire study workflow into one place — without unnecessary complexity.

---

## ✨ The Vision

Studying often means switching between PDF readers, answer keys, timers, notes, and separate mistake notebooks. MEOW aims to bring these pieces together in a single, thoughtfully designed experience.

### 📚 Your PDFs, Your Workflow

Bring your own study materials and organize them into books, topics, and tests. The long-term goal is to help transform static PDF question banks into structured, interactive study sessions.

### ✍️ Solve, Draw, Think

Work through questions one at a time. Draw directly on questions, write calculations, annotate diagrams, and select multiple-choice answers using touch or a supported stylus.

### 🎯 Answer Checking

Use answer keys to check completed tests and review results. Automated extraction and matching are planned, with manual verification whenever the app cannot confidently identify an answer.

### 🐾 A Mistake Notebook That Remembers

Keep incorrect and unanswered questions together. Revisit them, try again, and turn mistakes into opportunities to understand a topic more deeply.

### ⏱️ Progress That Means Something

Review correct, incorrect, and unanswered questions, completion time, and study history. Future versions aim to include topic-level insights and personalized review suggestions.

### 🧠 AI-Assisted Learning

The long-term vision includes optional AI-powered explanations to help users understand difficult questions and their solutions. AI features should remain optional and clearly communicate when study content is sent to an external service.

### 🐈 A Study Companion With Personality

MEOW features an original black-cat mascot designed to make the experience feel welcoming without turning a serious study tool into a children's game.

---

## 🛠️ Development Status

**MEOW is an early-stage personal project under development.**

This repository documents the project's vision, design direction, and intended technical approach. Features described here are planned goals and may not yet be implemented or available for use.

The project is being developed gradually, with an emphasis on learning, experimentation, and long-term quality rather than rushing toward a release.

## 🧭 Design Principles

- **Focus over clutter** — Keep the interface clear and purposeful.
- **Useful over complicated** — Every feature should solve a real problem.
- **Local-first where practical** — Keep study materials and progress on the device whenever feasible.
- **Honest automation** — Uncertain results must be reviewable rather than presented as guaranteed truth.
- **Progress without pressure** — Help students build better study habits without unnecessary distractions.
- **Built to grow** — Create a reliable foundation that can evolve over time.
- **Privacy by design** — Make data handling transparent and give users meaningful control.

## 🧰 Planned Technology

The intended Android technology stack includes:

- **Kotlin** — Primary application language.
- **Jetpack Compose** — Modern native Android UI.
- **Android PDF rendering** — Document viewing and page rendering.
- **PDF text extraction and on-device OCR** — Assistance with identifying question content.
- **Room** — Structured local persistence for books, tests, answers, and progress.
- **Vector-based annotations** — Scalable storage for handwriting and drawings.

These choices are provisional and may evolve as development and testing progress.

## 🗺️ Roadmap

The roadmap describes the intended direction, not a promise that each feature is already available.

### Foundation

- [ ] Establish the Android project and a reliable build workflow
- [ ] Define the core data model and local storage strategy
- [ ] Build the main library and navigation experience
- [ ] Import and display PDF documents

### Study Experience

- [ ] Create tests from selected PDF page ranges
- [ ] Navigate through questions individually
- [ ] Add drawing, handwriting, and annotation tools
- [ ] Select answers and mark questions as unanswered
- [ ] Save progress automatically and resume sessions

### Results and Review

- [ ] Enter and manage answer keys
- [ ] Calculate correct, incorrect, and unanswered totals
- [ ] Track completion time
- [ ] Build the mistake notebook
- [ ] Reattempt saved questions
- [ ] Add reliable backup and restore options

### Smarter PDF Processing

- [ ] Extract text from supported PDFs
- [ ] Explore OCR for scanned documents
- [ ] Suggest question boundaries and test divisions
- [ ] Assist with answer-key extraction and matching
- [ ] Explore topic classification
- [ ] Provide correction and verification flows for uncertain results

### Personalization and Intelligence

- [ ] Explore optional AI-powered explanations
- [ ] Add topic-level progress insights
- [ ] Introduce a spaced-review workflow
- [ ] Develop a small set of expressive mascot states
- [ ] Improve accessibility, performance, and overall polish

## 🔒 Privacy and Data

The intended direction is to keep personal study materials, annotations, and progress on-device wherever practical.

Any future online or AI-powered feature that processes user content should clearly explain what information is sent, where it goes, and how it is used. Sensitive information and API credentials must be handled carefully.

The exact privacy and backup behavior will be documented as the relevant features are implemented.

## 📖 Project Philosophy

MEOW is being built as a long-term personal project. There is no rushed release deadline and no intention to sacrifice quality just to ship quickly.

The goal is to make something genuinely useful — one that can grow from a simple study workflow into a complete learning companion, one thoughtful improvement at a time.

> One question at a time. One mistake at a time. One step forward.

## 📄 License and Usage

MEOW is currently published without an open-source license. All rights are reserved by the copyright holder.

You may view the source code on GitHub. No additional permission is granted to use, reproduce, modify, redistribute, or incorporate the code into other projects.

For permission requests, commercial licensing, or collaboration inquiries, please contact the repository owner.

Third-party dependencies, assets, and libraries remain subject to their respective licenses and terms.

## 🐈 MEOW

**Make every attempt worthwhile.**

Built with curiosity, patience, and a little cat energy.
