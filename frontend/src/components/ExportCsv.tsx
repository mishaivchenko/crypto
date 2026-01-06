import { Button } from "./ui/button";

type Header<T> = { key: keyof T; label: string };

type Props<T extends Record<string, any>> = {
    filename: string;
    rows: T[];
    headers?: Array<Header<T>>;
};

export function ExportCsv<T extends Record<string, any>>({ filename, rows, headers }: Props<T>) {
    const onClick = () => {
        if (!rows || rows.length === 0) {
            download(filename, ""); // пустой файл, ок
            return;
        }

        // 1) Колонки-ключи для доступа к данным
        const colKeys: Array<keyof T> = headers
            ? headers.map(h => h.key)
            : (Object.keys(rows[0]) as Array<keyof T>);

        // 2) Текст заголовков
        const head = (headers
                ? headers.map(h => h.label)
                : colKeys.map(k => String(k))
        )
            .map(escapeCsv)
            .join(",");

        // 3) Тело
        const body = rows
            .map(r =>
                colKeys
                    .map(k => escapeCsv(String(r[k] ?? "")))
                    .join(",")
            )
            .join("\n");

        download(filename, head + "\n" + body);
    };

    return (
        <Button variant="outline" onClick={onClick}>Export CSV</Button>
    );
}

function escapeCsv(s: string) {
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

function download(filename: string, content: string) {
    const blob = new Blob([content], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
}
