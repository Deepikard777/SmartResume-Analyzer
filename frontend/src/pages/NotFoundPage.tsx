import { Link } from 'react-router-dom';
import { Card, EmptyPanel } from '../components/ui';

export function NotFoundPage() {
  return (
    <div className="w-full max-w-3xl mx-auto px-gutter-mobile lg:px-gutter py-space-xl">
      <Card>
        <EmptyPanel
          icon="explore_off"
          title="Page not found"
          description="That route does not exist in this application."
          action={
            <Link
              to="/"
              className="mt-space-xs inline-flex items-center gap-space-xs px-space-md py-2 rounded-lg bg-primary-container text-on-primary font-body-md text-body-md font-medium hover:bg-primary transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]">dashboard</span>
              <span>Back to dashboard</span>
            </Link>
          }
        />
      </Card>
    </div>
  );
}
