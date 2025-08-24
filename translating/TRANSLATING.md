# Translating BaldPhone to Other Languages

If you encounter any problems, please [open an issue](https://github.com/DamianKuzmiak/BaldPhoneNeo/issues) or contact me at neo.baldphone@gmail.com.

## Available Languages

- [Brazilian Portuguese](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-pt-rBR/strings.xml) - translated by [Kellyson Antunes](https://github.com/kellysonantunes)
- [Bulgarian](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-bg/strings.xml) - translated by Ivaylo Dimitrov
- [Chinese](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-zh/strings.xml) - translated by [thomassth](https://github.com/thomassth)
- [Czech](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-cs/strings.xml) - translated by Tadeáš Horák
- [Danish](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-da/strings.xml) (not finished yet) - translated by ["a happy user"](https://github.com/DBC-226)
- [Dutch](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-nl/strings.xml) - translated by Floor van den Heuvel
- [English](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values/strings.xml)
- [French](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-fr/strings.xml) - translated by [Primokorn](https://github.com/Primokorn)
- [German](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-de/strings.xml) - translated by [Caibot](https://github.com/Caibot)
- [Greek](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-el/strings.xml) - translated by Zafeiris Ganas
- [Hebrew](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-iw/strings.xml) - translated by Uriah Shaul Mandel
- [Indonesian](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-id/strings.xml) - translated by [Sahri Riza Umami](https://github.com/rizaumami)
- [Italian](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-it/strings.xml) - translated by Dario Mastromattei
- [Korean](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-ko/strings.xml) - translated by [SIMSEUNGMIN](https://github.com/SIMSEUNGMIN)
- [Polish](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-pl/strings.xml) - translated by Qik
- [Portuguese](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-pt/strings.xml) - translated by Vladyslav Rudakevych
- [Romanian](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-ro/strings.xml) - translated by Tibz Leet
- [Russian](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-ru/strings.xml) - translated by [Vintic](https://github.com/Vintic)
- [Slovenian](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-sl/strings.xml) - translated by Klemen Skerbiš
- [Spanish](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-es/strings.xml) - translated by [Victor Arribas](https://github.com/varhub) and Ismael Ferreras Morezuelas (Swyter)
- [Swedish](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-sv/strings.xml) - translated by [Patrik Bogren](https://github.com/mmFooD)
- [Turkish](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values-tr/strings.xml) (not finished yet) - translated by Samii571

## How to Contribute

There are two ways to contribute:

### Option 1: For Non-Git Users

1.  Open the [English `strings.xml` file](https://github.com/DamianKuzmiak/BaldPhoneNeo/blob/master/app/src/main/res/values/strings.xml).
2.  Click the "Raw" button to view the plain text.
3.  Copy the entire content into a text editor.
4.  Translate the text within the `<string>` tags. For example, change `<string name="app_name">BaldPhone</string>` to `<string name="app_name">Your Translation</string>`.
5.  Save the translated file and email it to neo.baldphone@gmail.com, including your name for credit.

### Option 2: For Git Users

1.  **Fork** the repository.
2.  Create a new branch named `translation-xx` (replace `xx` with the [ISO 639-1 language code](https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes)).
3.  Copy the `app/src/main/res/values` directory and rename it to `app/src/main/res/values-xx` (again, using the language code).
4.  In the new `values-xx` folder, translate the `strings.xml` file.
5.  Add your name and language to this `TRANSLATING.md` file.
6.  **Commit** your changes and open a **pull request**.

## Fixing an Existing Translation

1.  Navigate to the `strings.xml` file for the language you want to fix from the list above.
2.  Click the "edit this file" button (pencil icon).
3.  Make your corrections.
4.  Propose the changes.
