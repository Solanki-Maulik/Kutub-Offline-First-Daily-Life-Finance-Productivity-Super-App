with open("app/src/main/java/com/example/ui/screens/DebtsScreen.kt", "r") as f:
    content = f.read()

content = content.replace("                    }\n                }\n            }\n        }\n    }\n}\n", "                    }\n                }\n            }\n        }\n    }\n}\n}\n")

with open("app/src/main/java/com/example/ui/screens/DebtsScreen.kt", "w") as f:
    f.write(content)
