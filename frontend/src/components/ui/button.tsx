import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { twMerge } from "tailwind-merge";

const buttonVariants = cva(
    "inline-flex items-center justify-center whitespace-nowrap rounded-2xl text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50 disabled:pointer-events-none h-9 px-4",
    {
        variants: {
            variant: {
                default: "bg-primary text-white hover:opacity-90",
                outline: "border border-input bg-transparent hover:bg-muted",
                ghost: "hover:bg-muted",
                destructive: "bg-danger text-white hover:opacity-90",
                success: "bg-success text-white hover:opacity-90"
            },
            size: {
                sm: "h-8 px-3 text-sm",
                md: "h-9 px-4",
                lg: "h-11 px-6 text-base",
                icon: "h-9 w-9 p-0"
            }
        },
        defaultVariants: {
            variant: "default",
            size: "md"
        }
    }
);

export interface ButtonProps
    extends React.ButtonHTMLAttributes<HTMLButtonElement>,
        VariantProps<typeof buttonVariants> {}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
    ({ className, variant, size, ...props }, ref) => (
        <button ref={ref} className={twMerge(buttonVariants({ variant, size }), className)} {...props} />
    )
);
Button.displayName = "Button";
