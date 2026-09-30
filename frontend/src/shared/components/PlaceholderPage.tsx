interface PlaceholderPageProps {
  title: string;
  description?: string;
}

export function PlaceholderPage({ title, description }: PlaceholderPageProps) {
  return (
    <section>
      <h1 className="text-2xl font-bold text-stone-900">{title}</h1>

      {description && <p className="mt-2 text-sm leading-6 text-stone-500">{description}</p>}
    </section>
  );
}
