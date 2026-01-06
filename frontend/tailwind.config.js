module.exports = {
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      colors: {
        ink: "#1c1a17",
        muted: "#6a6158",
        line: "#e4d8cb",
        cream: "#f7f1e6",
        sand: "#efe3d6",
        accent: "#c65a3b",
        "accent-strong": "#d4a24c",
        jade: "#1f8a70"
      },
      boxShadow: {
        soft: "0 18px 45px rgba(25, 18, 10, 0.12)",
        glow: "0 16px 30px rgba(198, 90, 59, 0.28)"
      },
      fontFamily: {
        sans: ["Sora", "ui-sans-serif", "system-ui", "sans-serif"],
        brand: ["Marcellus", "serif"]
      }
    }
  },
  plugins: []
};
