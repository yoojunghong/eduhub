import { Route, Routes, useParams } from 'react-router-dom';
import { Typography } from '@mui/material';
import { CourseListPage } from './pages/CourseListPage';
import { CourseDetailPage, CourseNotFound } from './pages/CourseDetailPage';
import { CourseAdminPage } from './pages/admin/CourseAdminPage';
import { CourseAdminDetailPage } from './pages/admin/CourseAdminDetailPage';

/** 주소의 :id 를 양의 정수로 바꾼다. 아니면 undefined. */
function useIdParam() {
  const id = Number(useParams().id);
  return Number.isInteger(id) && id > 0 ? id : undefined;
}

/** /courses/:id 의 id를 숫자로 바꾼다. 숫자가 아니면 API를 부르지 않고 바로 안내한다. */
function CourseDetailRoute() {
  const id = useIdParam();
  if (id === undefined) return <CourseNotFound />;
  return <CourseDetailPage key={id} id={id} />;
}

function CourseAdminDetailRoute() {
  const id = useIdParam();
  if (id === undefined) return <CourseNotFound />;
  return <CourseAdminDetailPage key={id} id={id} />;
}

export function App() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <>
            <Typography variant="h4" component="h1" gutterBottom>
              교육과정
            </Typography>
            <CourseListPage />
          </>
        }
      />
      <Route path="/courses/:id" element={<CourseDetailRoute />} />
      <Route path="/admin" element={<CourseAdminPage />} />
      <Route path="/admin/courses/:id" element={<CourseAdminDetailRoute />} />
      <Route path="*" element={<CourseNotFound />} />
    </Routes>
  );
}
