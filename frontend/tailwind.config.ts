import type { Config } from "tailwindcss";

export default {
    darkMode: ["class"],
    content: ["./index.html", "./src/**/*.{ts,tsx}"],
    theme: {
        extend: {
            container: {
                center: true,
                padding: "1rem"
            },
            colors: {
                background: "hsl(0 0% 100%)",
                foreground: "hsl(222.2 47.4% 11.2%)",
                muted: {
                    DEFAULT: "hsl(210 40% 96.1%)",
                    foreground: "hsl(215.4 16.3% 46.9%)"
                },
                card: {
                    DEFAULT: "hsl(0 0% 100%)",
                    foreground: "hsl(222.2 47.4% 11.2%)"
                },
                border: "hsl(214.3 31.8% 91.4%)",
                input: "hsl(214.3 31.8% 91.4%)",
                ring: "hsl(222.2 84% 4.9%)",
                primary: {
                    DEFAULT: "hsl(222.2 47.4% 11.2%)",
                    foreground: "hsl(210 40% 98%)"
                },
                success: "hsl(142.1 76.2% 36.3%)",
                warning: "hsl(38 92% 50%)",
                danger: "hsl(0 84% 60%)",
                neutral: "hsl(215 20% 65%)"
            },
            borderRadius: {
                xl: "1rem",
                "2xl": "1.25rem"
            }
        }
    },
    plugins: []
} satisfies Config;
